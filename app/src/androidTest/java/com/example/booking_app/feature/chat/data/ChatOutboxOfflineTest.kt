package com.example.booking_app.feature.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.booking_app.BuildConfig
import com.example.booking_app.core.network.AccountDeletedBus
import com.example.booking_app.core.network.AccountLockedBus
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.core.network.IdTokenProvider
import com.example.booking_app.core.util.SystemAppClock
import com.example.booking_app.feature.chat.data.dto.ChatSendRequest
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.example.booking_app.feature.chat.domain.toChatError
import com.example.booking_app.testing.EmulatorAdmin
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * T024 (SC-004): test Android trên Firebase Emulator (Auth + Firestore) + Supabase local (`chat-send`).
 *
 * - Tạo tài khoản PLAYER trên Auth Emulator (có claim `appRole = PLAYER`) và seed cuộc trò chuyện
 *   qua [EmulatorAdmin] (REST admin bỏ qua Rules), KHÔNG bao giờ ghi thẳng `messages` bằng client SDK
 *   (`firestore.rules`: `allow write: if false`).
 * - Tắt mạng (`FirebaseFirestore.disableNetwork` + chặn HTTP tới Supabase), gửi tin, đợi 20 giây,
 *   bật mạng lại → [ChatOutbox] tự lắng nghe mạng gọi Edge Function `chat-send` thật đúng 1 lần;
 *   đọc lại bằng client Firestore đã đăng nhập (`Source.SERVER`) xác nhận đúng 1 document do server ghi;
 *   lặp 5 lần.
 *
 * Yêu cầu khi chạy (`USE_EMULATOR=true` trong `local.properties`, Supabase local chạy `chat-send`
 * trỏ vào Firebase Emulator):
 * `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.chat.data.ChatOutboxOfflineTest`
 */
@RunWith(AndroidJUnit4::class)
class ChatOutboxOfflineTest {

    private class ToggleableNetworkMonitor(initialOnline: Boolean) : NetworkMonitor {
        private val state = MutableStateFlow(initialOnline)
        override val isOnline: Flow<Boolean> = state
        override fun isCurrentlyOnline(): Boolean = state.value
        fun setOnline(online: Boolean) {
            state.value = online
        }
    }

    private lateinit var projectId: String
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var admin: EmulatorAdmin
    private lateinit var supabase: SupabaseClient
    private lateinit var chatSendApi: SupabaseChatSendApi
    private lateinit var playerUid: String
    private lateinit var chatId: String
    private val email = "t024.${System.currentTimeMillis()}@example.com"
    private val createdMessageIds = mutableListOf<String>()

    @Before
    fun setUp() = runBlocking {
        assumeTrue("Chỉ chạy với Firebase Emulator (USE_EMULATOR=true)", BuildConfig.USE_EMULATOR == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val default = FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()
        projectId = default.options.projectId!!
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(context, default.options, APP_NAME)

        auth = FirebaseAuth.getInstance(app).apply {
            runCatching { useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT) }
        }
        firestore = FirebaseFirestore.getInstance(app).apply {
            runCatching { useEmulator(EMULATOR_HOST, FIRESTORE_EMULATOR_PORT) }
        }
        admin = EmulatorAdmin(projectId)

        playerUid = createPlayerAccount()
        chatId = listOf(OWNER_UID, playerUid).sorted().joinToString("_")
        seedChat()

        // Đăng nhập client để có Firebase ID token (claim appRole=PLAYER) gọi chat-send và đọc messages theo Rules.
        auth.signInWithEmailAndPassword(email, PASSWORD).await()
        auth.currentUser!!.getIdToken(true).await()

        val idTokenProvider = object : IdTokenProvider {
            override suspend fun getToken(forceRefresh: Boolean): String {
                val user = auth.currentUser ?: throw AppError.NotSignedIn
                return user.getIdToken(forceRefresh).await().token ?: throw AppError.NotSignedIn
            }
        }
        supabase = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            accessToken = {
                try {
                    idTokenProvider.getToken(forceRefresh = false)
                } catch (_: AppError.NotSignedIn) {
                    null
                }
            }
            install(Functions)
        }
        val functionErrors = FunctionErrors(
            idTokenProvider = idTokenProvider,
            lockedBus = AccountLockedBus(),
            deletedBus = AccountDeletedBus(),
        )
        chatSendApi = SupabaseChatSendApi(supabase = supabase, functionErrors = functionErrors)
    }

    @After
    fun tearDown() {
        if (::firestore.isInitialized) {
            runCatching { runBlocking { firestore.enableNetwork().await() } }
        }
        if (::supabase.isInitialized) {
            runCatching { runBlocking { supabase.close() } }
        }
        if (::auth.isInitialized) {
            runCatching { auth.signOut() }
        }
        if (::admin.isInitialized && ::chatId.isInitialized) {
            createdMessageIds.forEach { msgId ->
                runCatching { admin.delete("chats/$chatId/messages/$msgId") }
            }
            runCatching { admin.delete("chats/$chatId/participants/$playerUid") }
            runCatching { admin.delete("chats/$chatId/participants/$OWNER_UID") }
            runCatching { admin.delete("chats/$chatId") }
            runCatching { admin.delete("users/$playerUid") }
            deleteAccount(playerUid)
        }
    }

    @Test
    fun guiTinKhiMatMangRoiBatMangLaiDungMotTinLap5Lan() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val network = ToggleableNetworkMonitor(initialOnline = false)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "chat_outbox_offline_${UUID.randomUUID()}.preferences_pb")
        }

        // Chặn HTTP khi tắt mạng; khi bật mạng lại, gọi Edge Function `chat-send` thật trên Supabase local.
        val sendCalls = ConcurrentHashMap<String, Int>()
        val transport = object : ChatOutboxTransport {
            override fun currentUid(): String = playerUid

            override suspend fun send(entry: OutboxEntry): Result<Unit> {
                if (!network.isCurrentlyOnline()) return Result.failure(ChatError.Network())
                sendCalls.compute(entry.messageId) { _, prev -> (prev ?: 0) + 1 }
                return try {
                    chatSendApi.invoke(
                        ChatSendRequest(
                            chatId = entry.chatId,
                            messageId = entry.messageId,
                            text = entry.text,
                            imagePath = entry.imagePath,
                        ),
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e.toChatError())
                }
            }

            override suspend fun uploadImage(chatId: String, messageId: String, localUri: String): Result<String> =
                Result.failure(ChatError.Network())

            override suspend fun messageExistsOnServer(chatId: String, messageId: String): Boolean =
                try {
                    firestore.collection("chats").document(chatId)
                        .collection("messages").document(messageId)
                        .get(Source.SERVER)
                        .await()
                        .exists()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }

            override suspend fun discardMedia(chatId: String, messageId: String) = Unit
        }

        val outbox = ChatOutbox(
            dataStore = dataStore,
            transport = transport,
            networkMonitor = network,
            clock = SystemAppClock(),
            scope = scope,
            ioDispatcher = Dispatchers.IO,
        )

        try {
            repeat(5) { iteration ->
                // 1. Tắt mạng Firestore + chặn HTTP Supabase
                network.setOnline(false)
                firestore.disableNetwork().await()

                // 2. Gửi tin khi đang mất mạng
                val messageId = UUID.randomUUID().toString()
                val expectedText = "Tin offline lần ${iteration + 1}"
                createdMessageIds += messageId
                outbox.enqueue(
                    OutboxEntry(
                        messageId = messageId,
                        chatId = chatId,
                        text = expectedText,
                        createdAtLocal = System.currentTimeMillis(),
                        senderId = playerUid,
                    ),
                )
                assertEquals(1, outbox.observe(chatId).first().size)

                // 3. Đợi 20 giây khi mất mạng
                delay(20_000L)

                // 4. Bật mạng lại — KHÔNG gọi flushRetryable() thủ công, kiểm chứng cơ chế tự gửi lại qua chat-send.
                firestore.enableNetwork().await()
                network.setOnline(true)

                // 5. Đợi outbox tự gửi xong và đọc lại document do server (`chat-send`) ghi trên Firestore Emulator.
                withTimeout(15_000L) {
                    outbox.observe(chatId).first { it.isEmpty() }
                }
                assertEquals(1, sendCalls[messageId])

                val serverDoc = firestore.collection("chats").document(chatId)
                    .collection("messages").document(messageId)
                    .get(Source.SERVER)
                    .await()
                assertTrue("Tin $messageId phải do server chat-send ghi trên Firestore Emulator", serverDoc.exists())
                assertEquals(expectedText, serverDoc.getString("text"))
                assertEquals(playerUid, serverDoc.getString("senderId"))

                // Khẳng định không tạo tin trùng trên server sau mỗi chu kỳ mất mạng → có mạng.
                val allServerMessages = firestore.collection("chats").document(chatId)
                    .collection("messages")
                    .get(Source.SERVER)
                    .await()
                assertEquals(iteration + 1, allServerMessages.size())
            }
        } finally {
            outbox.close()
            scope.cancel()
        }
    }

    // ---------- Auth Emulator + Seed Firestore ----------

    private fun seedChat() {
        val now = Instant.now()
        admin.put(
            "users/$playerUid",
            mapOf(
                "displayName" to "Người chơi T024",
                "email" to email,
                "role" to "PLAYER",
                "accountStatus" to "ACTIVE",
                "ownerStatus" to "NONE",
            ),
        )
        admin.put(
            "chats/$chatId",
            mapOf(
                "type" to "DIRECT",
                "memberIds" to listOf(OWNER_UID, playerUid),
                "ownerId" to OWNER_UID,
                "playerId" to playerUid,
                "status" to "ACTIVE",
                "unreadCounts" to mapOf(OWNER_UID to 0L, playerUid to 0L),
                "memberSnapshots" to mapOf(
                    OWNER_UID to mapOf("displayName" to "Chủ sân T024", "avatarUrl" to null),
                    playerUid to mapOf("displayName" to "Người chơi T024", "avatarUrl" to null),
                ),
                "createdAt" to now,
                "updatedAt" to now,
            ),
        )
        admin.put(
            "chats/$chatId/participants/$playerUid",
            mapOf(
                "userId" to playerUid,
                "chatId" to chatId,
                "muted" to false,
                "blockedOther" to false,
                "userSnapshot" to mapOf("displayName" to "Người chơi T024", "avatarUrl" to null),
            ),
        )
        admin.put(
            "chats/$chatId/participants/$OWNER_UID",
            mapOf(
                "userId" to OWNER_UID,
                "chatId" to chatId,
                "muted" to false,
                "blockedOther" to false,
                "userSnapshot" to mapOf("displayName" to "Chủ sân T024", "avatarUrl" to null),
            ),
        )
    }

    private fun createPlayerAccount(): String {
        val signUp = authRequest(
            "identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key",
            JSONObject().put("email", email).put("password", PASSWORD).put("returnSecureToken", true),
            admin = false,
        )
        val localId = signUp.getString("localId")
        authRequest(
            "identitytoolkit.googleapis.com/v1/projects/$projectId/accounts:update",
            JSONObject()
                .put("localId", localId)
                .put("emailVerified", true)
                .put("customAttributes", JSONObject().put("appRole", "PLAYER").toString()),
            admin = true,
        )
        return localId
    }

    private fun deleteAccount(localId: String) {
        runCatching {
            authRequest(
                "identitytoolkit.googleapis.com/v1/projects/$projectId/accounts:delete",
                JSONObject().put("localId", localId),
                admin = true,
            )
        }
    }

    private fun authRequest(path: String, body: JSONObject, admin: Boolean): JSONObject {
        val connection = URL("http://$EMULATOR_HOST:$AUTH_EMULATOR_PORT/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (admin) connection.setRequestProperty("Authorization", "Bearer owner")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            check(code in 200..299) { "Auth Emulator $path → $code $text" }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val APP_NAME = "chat-outbox-offline-test"
        const val EMULATOR_HOST = "10.0.2.2"
        const val AUTH_EMULATOR_PORT = 9099
        const val FIRESTORE_EMULATOR_PORT = 8080
        const val OWNER_UID = "t024_owner"
        const val PASSWORD = "abc12345"
    }
}

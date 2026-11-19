package com.example.booking_app.feature.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.booking_app.core.util.SystemAppClock
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * T024 (SC-004): test Android trên Firebase Emulator + Supabase local.
 * Tắt mạng (`FirebaseFirestore.disableNetwork` + chặn HTTP tới Supabase), gửi tin, đợi 20 giây,
 * bật mạng lại → `ChatOutbox` tự lắng nghe mạng gửi lại, đúng 1 document tin trên server; lặp 5 lần.
 *
 * Lưu ý: chạy trên Emulator đã seed 090 + 091 (`./gradlew :app:connectedDebugAndroidTest`).
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

    @Test
    fun guiTinKhiMatMangRoiBatMangLaiDungMotTinLap5Lan() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firestore = FirebaseFirestore.getInstance().apply {
            useEmulator("10.0.2.2", 8080)
        }
        val chatId = "owner1_player1"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val network = ToggleableNetworkMonitor(initialOnline = false)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "chat_outbox_offline_${UUID.randomUUID()}.preferences_pb")
        }

        // Chặn HTTP khi tắt mạng; khi bật mạng lại, ghi document thật lên Firestore Emulator và đếm mọi lần gọi send().
        val sendCalls = ConcurrentHashMap<String, Int>()
        val transport = object : ChatOutboxTransport {
            override suspend fun send(entry: OutboxEntry): Result<Unit> {
                if (!network.isCurrentlyOnline()) return Result.failure(ChatError.Network())
                sendCalls.compute(entry.messageId) { _, prev -> (prev ?: 0) + 1 }
                return try {
                    firestore.collection("chats").document(entry.chatId)
                        .collection("messages").document(entry.messageId)
                        .set(
                            mapOf(
                                "senderId" to "player1",
                                "text" to entry.text,
                                "status" to "NORMAL",
                                "createdAt" to FieldValue.serverTimestamp(),
                            ),
                        )
                        .await()
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(ChatError.Network())
                }
            }

            override suspend fun uploadImage(chatId: String, messageId: String, localUri: String): Result<String> =
                Result.failure(ChatError.Network())

            override suspend fun messageExistsOnServer(chatId: String, messageId: String): Boolean =
                runCatching {
                    firestore.collection("chats").document(chatId)
                        .collection("messages").document(messageId)
                        .get(Source.SERVER)
                        .await()
                        .exists()
                }.getOrDefault(false)

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
                outbox.enqueue(
                    OutboxEntry(
                        messageId = messageId,
                        chatId = chatId,
                        text = expectedText,
                        createdAtLocal = System.currentTimeMillis(),
                    ),
                )
                assertEquals(1, outbox.observe(chatId).first().size)

                // 3. Đợi 20 giây khi mất mạng
                delay(20_000L)

                // 4. Bật mạng lại — KHÔNG gọi flushRetryable() thủ công, kiểm chứng cơ chế tự gửi lại.
                firestore.enableNetwork().await()
                network.setOnline(true)

                // 5. Đợi outbox tự gửi xong và xác nhận đúng 1 document trên Firestore Emulator.
                withTimeout(15_000L) {
                    outbox.observe(chatId).first { it.isEmpty() }
                }
                assertEquals(1, sendCalls[messageId])

                val serverDoc = firestore.collection("chats").document(chatId)
                    .collection("messages").document(messageId)
                    .get(Source.SERVER)
                    .await()
                assertTrue("Tin $messageId phải tồn tại trên Firestore Emulator", serverDoc.exists())
                assertEquals(expectedText, serverDoc.getString("text"))
            }
        } finally {
            outbox.close()
            scope.cancel()
        }
    }
}

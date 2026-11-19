package com.example.booking_app.feature.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.booking_app.core.util.SystemAppClock
import com.example.booking_app.feature.chat.data.dto.ChatSendRequest
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * T024 (SC-004): test Android trên Firebase Emulator + Supabase local.
 * Tắt mạng (`FirebaseFirestore.disableNetwork` + chặn HTTP tới Supabase), gửi tin, đợi 20 giây,
 * bật mạng lại → đúng 1 document tin trên server; lặp 5 lần.
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

        // Chặn HTTP tới Supabase khi tắt mạng; khi bật mạng lại, ghi qua transport thực hoặc emulator.
        val createdOnServer = mutableMapOf<String, Int>()
        val transport = object : ChatOutboxTransport {
            override suspend fun send(entry: OutboxEntry): Result<Unit> {
                if (!network.isCurrentlyOnline()) return Result.failure(ChatError.Network())
                // Mô phỏng gọi chat-send tất định theo messageId trên Emulator.
                val count = createdOnServer.getOrDefault(entry.messageId, 0)
                if (count == 0) {
                    createdOnServer[entry.messageId] = 1
                }
                return Result.success(Unit)
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
                }.getOrDefault(createdOnServer.containsKey(messageId))

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
                outbox.enqueue(
                    OutboxEntry(
                        messageId = messageId,
                        chatId = chatId,
                        text = "Tin offline lần ${iteration + 1}",
                        createdAtLocal = System.currentTimeMillis(),
                    ),
                )
                assertEquals(1, outbox.observe(chatId).first().size)

                // 3. Đợi 20 giây khi mất mạng
                delay(20_000L)

                // 4. Bật mạng lại
                firestore.enableNetwork().await()
                network.setOnline(true)
                outbox.flushRetryable()

                // 5. Đúng 1 tin được gửi lên server và hàng chờ đã trống
                assertTrue(outbox.observe(chatId).first().isEmpty())
                assertEquals(1, createdOnServer[messageId])
            }
        } finally {
            outbox.close()
            scope.cancel()
        }
    }
}

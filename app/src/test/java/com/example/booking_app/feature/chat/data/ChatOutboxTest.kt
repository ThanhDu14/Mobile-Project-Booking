package com.example.booking_app.feature.chat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.model.Delivery
import com.example.booking_app.feature.chat.domain.model.DiscardResult
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T019: unit test cho [ChatOutbox] (fake DataStore, fake clock, fake transport, fake network). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatOutboxTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private class FakeClock(var currentMs: Long = 1_000_000L) : AppClock {
        override fun now(): Long = currentMs
        override fun elapsedRealtime(): Long = currentMs
    }

    private class FakeNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
        private val flow = MutableStateFlow(initialOnline)
        override val isOnline: Flow<Boolean> = flow
        override fun isCurrentlyOnline(): Boolean = flow.value
        fun setOnline(online: Boolean) {
            flow.value = online
        }
    }

    private class FakeTransport : ChatOutboxTransport {
        var activeUid: String? = null
        val sentIds = mutableListOf<String>()
        val uploadedIds = mutableListOf<String>()
        val discardedMediaIds = mutableListOf<String>()
        val serverCheckIds = mutableListOf<String>()
        var serverExistingIds = mutableSetOf<String>()
        var sendHandler: suspend (OutboxEntry) -> Result<Unit> = { Result.success(Unit) }
        var uploadHandler: suspend (String, String, String) -> Result<String> = { chatId, msgId, _ ->
            Result.success("$chatId/$msgId.jpg")
        }

        override fun currentUid(): String? = activeUid

        override suspend fun send(entry: OutboxEntry): Result<Unit> {
            sentIds.add(entry.messageId)
            return sendHandler(entry)
        }

        override suspend fun uploadImage(chatId: String, messageId: String, localUri: String): Result<String> {
            uploadedIds.add(messageId)
            return uploadHandler(chatId, messageId, localUri)
        }

        override suspend fun messageExistsOnServer(chatId: String, messageId: String): Boolean {
            serverCheckIds.add(messageId)
            return messageId in serverExistingIds
        }

        override suspend fun discardMedia(chatId: String, messageId: String) {
            discardedMediaIds.add(messageId)
        }
    }

    private fun entry(
        id: String = "msg-1",
        chatId: String = "chat-1",
        text: String = "Xin chào",
        localImageUri: String? = null,
        createdAtLocal: Long = 1_000_000L,
    ) = OutboxEntry(
        messageId = id,
        chatId = chatId,
        text = text,
        localImageUri = localImageUri,
        createdAtLocal = createdAtLocal,
    )

    private fun TestScope.createOutbox(
        store: FakeDataStore = FakeDataStore(),
        transport: FakeTransport = FakeTransport(),
        network: FakeNetworkMonitor = FakeNetworkMonitor(true),
        clock: FakeClock = FakeClock(),
    ): ChatOutbox {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ChatOutbox(
            dataStore = store,
            transport = transport,
            networkMonitor = network,
            clock = clock,
            scope = this,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `them tin hien PENDING ngay va bo khoi outbox khi gui thanh cong`() = runTest {
        val gate = CompletableDeferred<Result<Unit>>()
        val transport = FakeTransport().apply { sendHandler = { gate.await() } }
        val outbox = createOutbox(transport = transport)

        outbox.enqueue(entry("m1"))
        val pending = outbox.observe("chat-1").first()
        assertEquals(1, pending.size)
        assertEquals(Delivery.PENDING, pending.first().status)

        runCurrent()
        gate.complete(Result.success(Unit))
        advanceUntilIdle()

        assertTrue(outbox.observe("chat-1").first().isEmpty())
        assertEquals(listOf("m1"), transport.sentIds)
        outbox.close()
    }

    @Test
    fun `mat mang thi giu tin va co mang lai tu gui cung messageId`() = runTest {
        val network = FakeNetworkMonitor(initialOnline = false)
        val transport = FakeTransport()
        val outbox = createOutbox(transport = transport, network = network)

        outbox.enqueue(entry("m-offline"))
        runCurrent()

        val kept = outbox.observe("chat-1").first()
        assertEquals(1, kept.size)
        assertEquals(Delivery.PENDING, kept.first().status)
        assertTrue(transport.sentIds.isEmpty())

        network.setOnline(true)
        advanceUntilIdle()

        assertTrue(outbox.observe("chat-1").first().isEmpty())
        assertEquals(listOf("m-offline"), transport.sentIds)
        outbox.close()
    }

    @Test
    fun `qua 60 giay chuyen FAILED nhung van tu gui lai khi co mang`() = runTest {
        val network = FakeNetworkMonitor(initialOnline = false)
        val clock = FakeClock(1_000_000L)
        val transport = FakeTransport()
        val outbox = createOutbox(transport = transport, network = network, clock = clock)

        outbox.enqueue(entry("m-timeout", createdAtLocal = clock.currentMs))
        runCurrent()

        clock.currentMs += ChatOutbox.TIMEOUT_MS
        advanceTimeBy(ChatOutbox.TIMEOUT_MS + 1)
        runCurrent()

        val failed = outbox.observe("chat-1").first()
        assertEquals(1, failed.size)
        assertEquals(Delivery.FAILED, failed.first().status)
        assertTrue(failed.first().retryable)

        network.setOnline(true)
        advanceUntilIdle()

        assertTrue(outbox.observe("chat-1").first().isEmpty())
        assertEquals(listOf("m-timeout"), transport.sentIds)
        outbox.close()
    }

    @Test
    fun `loi khong thu lai duoc chuyen FAILED ngay va khong tu gui khi co mang`() = runTest {
        for (error in listOf(ChatError.Blocked(), ChatError.NotParticipant(), ChatError.InvalidArgument())) {
            val network = FakeNetworkMonitor(initialOnline = true)
            val transport = FakeTransport().apply { sendHandler = { Result.failure(error) } }
            val outbox = createOutbox(transport = transport, network = network)

            val id = "m-${error.code}"
            outbox.enqueue(entry(id))
            advanceUntilIdle()

            val items = outbox.observe("chat-1").first()
            assertEquals(1, items.size)
            assertEquals(Delivery.FAILED, items.first().status)
            assertFalse(items.first().retryable)
            assertEquals(error.code, items.first().errorCode)

            // Rớt mạng rồi có mạng lại vẫn KHÔNG tự gửi lại.
            network.setOnline(false)
            runCurrent()
            network.setOnline(true)
            advanceUntilIdle()

            assertEquals(1, transport.sentIds.size)
            outbox.close()
        }
    }

    @Test
    fun `gui lai dung lai dung messageId`() = runTest {
        var attempt = 0
        val transport = FakeTransport().apply {
            sendHandler = {
                attempt++
                if (attempt == 1) Result.failure(ChatError.Blocked()) else Result.success(Unit)
            }
        }
        val outbox = createOutbox(transport = transport)

        outbox.enqueue(entry("m-retry"))
        advanceUntilIdle()
        assertEquals(Delivery.FAILED, outbox.observe("chat-1").first().first().status)

        outbox.retry("chat-1", "m-retry")
        advanceUntilIdle()

        assertTrue(outbox.observe("chat-1").first().isEmpty())
        assertEquals(listOf("m-retry", "m-retry"), transport.sentIds)
        outbox.close()
    }

    @Test
    fun `xoa khi server da co tin thi tra ALREADY_SENT va khong xoa anh`() = runTest {
        val network = FakeNetworkMonitor(initialOnline = true)
        val clock = FakeClock(1_000_000L)
        val transport = FakeTransport().apply {
            // Mô phỏng lần gửi trước commit trên server rồi rớt phản hồi (research R1).
            sendHandler = { e ->
                serverExistingIds.add(e.messageId)
                Result.failure(ChatError.Network())
            }
        }
        val outbox = createOutbox(transport = transport, network = network, clock = clock)
        try {
            outbox.enqueue(
                entry(
                    id = "m-exists",
                    localImageUri = "content://img/1",
                    createdAtLocal = clock.currentMs,
                ),
            )
            runCurrent()
            clock.currentMs += ChatOutbox.TIMEOUT_MS
            advanceTimeBy(ChatOutbox.TIMEOUT_MS + 1L)
            runCurrent()

            val result = outbox.discard("chat-1", "m-exists")
            advanceUntilIdle()
            assertEquals(DiscardResult.ALREADY_SENT, result)
            assertTrue(outbox.observe("chat-1").first().isEmpty())
            assertTrue(transport.discardedMediaIds.isEmpty())
        } finally {
            outbox.close()
        }
    }

    @Test
    fun `xoa khi server chua co tin thi xoa khoi outbox va goi discardMedia neu co anh`() = runTest {
        val network = FakeNetworkMonitor(initialOnline = false)
        val transport = FakeTransport()
        val outbox = createOutbox(transport = transport, network = network)
        try {
            outbox.enqueue(entry("m-discard", localImageUri = "content://img/2"))
            runCurrent()

            val result = outbox.discard("chat-1", "m-discard")
            advanceUntilIdle()
            assertEquals(DiscardResult.DISCARDED, result)
            assertTrue(outbox.observe("chat-1").first().isEmpty())
            assertTrue(transport.serverCheckIds.isEmpty())
            assertEquals(listOf("m-discard"), transport.discardedMediaIds)
        } finally {
            outbox.close()
        }
    }

    @Test
    fun `BUSY tu thu lai cung messageId`() = runTest {
        var attempt = 0
        val transport = FakeTransport().apply {
            sendHandler = {
                attempt++
                if (attempt <= 2) Result.failure(ChatError.Busy()) else Result.success(Unit)
            }
        }
        val outbox = createOutbox(transport = transport)

        outbox.enqueue(entry("m-busy"))
        advanceUntilIdle()

        assertTrue(outbox.observe("chat-1").first().isEmpty())
        assertEquals(listOf("m-busy", "m-busy", "m-busy"), transport.sentIds)
        outbox.close()
    }

    @Test
    fun `outbox giu tin sau khi tao lai instance gia lap app bi tat`() = runTest {
        val store = FakeDataStore()
        val network = FakeNetworkMonitor(initialOnline = false)
        val transport = FakeTransport()
        val clock = FakeClock(1_000_000L)

        val firstInstance = createOutbox(store = store, transport = transport, network = network, clock = clock)
        firstInstance.enqueue(entry("m-persist", text = "Còn sân không?"))
        runCurrent()
        firstInstance.close()

        // Tạo instance mới dùng chung DataStore (mô phỏng mở lại app).
        val secondInstance = createOutbox(store = store, transport = transport, network = network, clock = clock)
        val restored = secondInstance.observe("chat-1").first()
        assertEquals(1, restored.size)
        assertEquals("m-persist", restored.first().messageId)
        assertEquals("Còn sân không?", restored.first().text)

        network.setOnline(true)
        advanceUntilIdle()
        assertTrue(secondInstance.observe("chat-1").first().isEmpty())
        assertEquals(listOf("m-persist"), transport.sentIds)
        secondInstance.close()
    }

    @Test
    fun `gui tuan tu tung cuoc tro chuyen de giu thu tu`() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val transport = FakeTransport().apply {
            sendHandler = { e ->
                if (e.messageId == "m1") firstGate.await()
                order.add(e.messageId)
                Result.success(Unit)
            }
        }
        val clock = FakeClock(1_000_000L)
        val outbox = createOutbox(transport = transport, clock = clock)

        outbox.enqueue(entry("m1", createdAtLocal = 1_000_000L))
        outbox.enqueue(entry("m2", createdAtLocal = 1_000_001L))
        runCurrent()
        assertTrue(order.isEmpty())

        firstGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("m1", "m2"), order)
        outbox.close()
    }

    // ---------- T042: US4 Gửi ảnh trong outbox ----------

    @Test
    fun `tin anh tai anh truoc roi moi gui chat-send cung messageId`() = runTest {
        val sentImagePaths = mutableListOf<String?>()
        val transport = FakeTransport().apply {
            sendHandler = { e ->
                sentImagePaths.add(e.imagePath)
                Result.success(Unit)
            }
        }
        val outbox = createOutbox(transport = transport)

        outbox.enqueue(entry("m-img", text = "", localImageUri = "content://img/court.jpg"))
        advanceUntilIdle()

        assertEquals(listOf("m-img"), transport.uploadedIds)
        assertEquals(listOf("m-img"), transport.sentIds)
        assertEquals(listOf("chat-1/m-img.jpg"), sentImagePaths)
        assertTrue(outbox.observe("chat-1").first().isEmpty())
        outbox.close()
    }

    @Test
    fun `tai anh loi chuyen FAILED va gui lai tai lai roi gui cung messageId`() = runTest {
        var uploadAttempts = 0
        val transport = FakeTransport().apply {
            uploadHandler = { chatId, msgId, _ ->
                uploadAttempts++
                if (uploadAttempts == 1) {
                    Result.failure(ChatError.Network())
                } else {
                    Result.success("$chatId/$msgId.jpg")
                }
            }
        }
        val outbox = createOutbox(transport = transport)

        outbox.enqueue(entry("m-img-retry", text = "Mặt sân", localImageUri = "content://img/1"))
        advanceUntilIdle()

        val failed = outbox.observe("chat-1").first()
        assertEquals(1, failed.size)
        assertEquals(Delivery.FAILED, failed.first().status)
        assertTrue(transport.sentIds.isEmpty())

        // Bấm "Gửi lại": tải lại ảnh và gửi tin dùng lại đúng messageId.
        outbox.retry("chat-1", "m-img-retry")
        advanceUntilIdle()

        assertTrue(outbox.observe("chat-1").first().isEmpty())
        assertEquals(listOf("m-img-retry", "m-img-retry"), transport.uploadedIds)
        assertEquals(listOf("m-img-retry"), transport.sentIds)
        outbox.close()
    }

    @Test
    fun `tai anh loi roi bam Xoa goi discardMedia va bo khoi outbox`() = runTest {
        val transport = FakeTransport().apply {
            uploadHandler = { _, _, _ -> Result.failure(ChatError.Network()) }
        }
        val outbox = createOutbox(transport = transport)

        outbox.enqueue(entry("m-img-del", text = "", localImageUri = "content://img/bad"))
        advanceUntilIdle()
        assertEquals(Delivery.FAILED, outbox.observe("chat-1").first().single().status)

        val outcome = outbox.discard("chat-1", "m-img-del")
        advanceUntilIdle()
        assertEquals(DiscardResult.DISCARDED, outcome)
        assertTrue(outbox.observe("chat-1").first().isEmpty())
        assertEquals(listOf("m-img-del"), transport.discardedMediaIds)
        outbox.close()
    }

    @Test
    fun `server bao PHOTO_MISSING thi xoa imagePath de gui lai tai anh tu dau`() = runTest {
        var sendAttempts = 0
        val transport = FakeTransport().apply {
            sendHandler = {
                sendAttempts++
                if (sendAttempts == 1) Result.failure(ChatError.PhotoMissing()) else Result.success(Unit)
            }
        }
        val outbox = createOutbox(transport = transport)

        outbox.enqueue(entry("m-photo-miss", text = "", localImageUri = "content://img/2"))
        advanceUntilIdle()

        val failed = outbox.observe("chat-1").first().single()
        assertEquals(Delivery.FAILED, failed.status)
        assertEquals(null, failed.imagePath)

        outbox.retry("chat-1", "m-photo-miss")
        advanceUntilIdle()

        assertEquals(listOf("m-photo-miss", "m-photo-miss"), transport.uploadedIds)
        assertEquals(listOf("m-photo-miss", "m-photo-miss"), transport.sentIds)
        assertTrue(outbox.observe("chat-1").first().isEmpty())
        outbox.close()
    }

    // ---------- Review Argon Setup #2, #3, #4, #6, #9 ----------

    @Test
    fun `flushRetryable gap loi mang o tin dau khong lam tin sau bi ket PENDING`() = runTest {
        val network = FakeNetworkMonitor(initialOnline = false)
        val clock = FakeClock(1_000_000L)
        val transport = FakeTransport().apply {
            sendHandler = { Result.failure(ChatError.Network()) }
        }
        val outbox = createOutbox(transport = transport, network = network, clock = clock)
        try {
            outbox.enqueue(entry("m1", createdAtLocal = clock.currentMs))
            outbox.enqueue(entry("m2", createdAtLocal = clock.currentMs + 10L))
            runCurrent()

            clock.currentMs += ChatOutbox.TIMEOUT_MS + 100L
            advanceTimeBy(ChatOutbox.TIMEOUT_MS + 200L)
            runCurrent()

            val beforeOnline = outbox.observe("chat-1").first()
            assertEquals(listOf(Delivery.FAILED, Delivery.FAILED), beforeOnline.map { it.status })

            // Có mạng lại nhưng request tin đầu vẫn lỗi mạng → tin 2 KHÔNG được kẹt ở PENDING.
            network.setOnline(true)
            advanceUntilIdle()

            val afterFail = outbox.observe("chat-1").first()
            assertEquals(2, afterFail.size)
            assertEquals(listOf(Delivery.FAILED, Delivery.FAILED), afterFail.map { it.status })
            assertTrue(afterFail.all { it.retryable })
            assertEquals(listOf("m1"), transport.sentIds)
        } finally {
            outbox.close()
        }
    }

    @Test
    fun `loi mang tam thoi luc dang online tu thu lai theo backoff va gui thanh cong cung messageId`() = runTest {
        val network = FakeNetworkMonitor(initialOnline = true)
        val clock = FakeClock(1_000_000L)
        var attempts = 0
        val transport = FakeTransport().apply {
            sendHandler = {
                attempts++
                if (attempts < 3) Result.failure(ChatError.Network()) else Result.success(Unit)
            }
        }
        val outbox = createOutbox(transport = transport, network = network, clock = clock)
        try {
            outbox.enqueue(entry("m-backoff", createdAtLocal = clock.currentMs))
            runCurrent()
            // Lần 1 thất bại → vẫn PENDING chờ backoff.
            assertEquals(1, attempts)
            assertEquals(Delivery.PENDING, outbox.observe("chat-1").first().single().status)

            // Qua nhịp backoff 2s (lần 2 vẫn lỗi) + 4s (lần 3 thành công).
            advanceTimeBy(2_000L + 4_000L + 100L)
            runCurrent()

            assertTrue(outbox.observe("chat-1").first().isEmpty())
            assertEquals(listOf("m-backoff", "m-backoff", "m-backoff"), transport.sentIds)
        } finally {
            outbox.close()
        }
    }

    @Test
    fun `mo lai app luc van offline khoi phuc bo dem 60 giay cho tin PENDING`() = runTest {
        val store = FakeDataStore()
        val network = FakeNetworkMonitor(initialOnline = false)
        val clock = FakeClock(1_000_000L)
        val transport = FakeTransport()

        val first = createOutbox(store = store, transport = transport, network = network, clock = clock)
        first.enqueue(entry("m-offline-restart", createdAtLocal = clock.currentMs))
        runCurrent()
        first.close()

        // Giả lập app mở lại sau 20 giây, vẫn đang offline.
        clock.currentMs += 20_000L
        val second = createOutbox(store = store, transport = transport, network = network, clock = clock)
        try {
            runCurrent()
            assertEquals(Delivery.PENDING, second.observe("chat-1").first().single().status)

            // Sau thêm 40 giây (đủ 60s), timer đã khôi phục phải chuyển tin sang FAILED.
            clock.currentMs += 40_000L
            advanceTimeBy(40_001L)
            runCurrent()

            val failed = second.observe("chat-1").first().single()
            assertEquals(Delivery.FAILED, failed.status)
            assertTrue(failed.retryable)
        } finally {
            second.close()
        }
    }

    @Test
    fun `doi tai khoan tren cung may khong hien va khong gui tin outbox cua tai khoan truoc`() = runTest {
        val store = FakeDataStore()
        val network = FakeNetworkMonitor(initialOnline = false)
        val transport = FakeTransport().apply { activeUid = "player1" }
        val outbox = createOutbox(store = store, transport = transport, network = network)
        try {
            outbox.enqueue(entry("m-p1", text = "Tin của player1"))
            runCurrent()
            assertEquals(1, outbox.observe("chat-1").first().size)

            // Đăng xuất player1, đăng nhập owner1 rồi có mạng lại.
            transport.activeUid = "owner1"
            assertTrue(outbox.observe("chat-1").first().isEmpty())

            network.setOnline(true)
            advanceUntilIdle()
            assertTrue(transport.sentIds.isEmpty())
        } finally {
            outbox.close()
        }
    }
}

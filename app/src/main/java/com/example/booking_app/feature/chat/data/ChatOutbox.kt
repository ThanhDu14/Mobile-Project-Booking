package com.example.booking_app.feature.chat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.booking_app.core.di.ApplicationScope
import com.example.booking_app.core.di.IoDispatcher
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.data.di.ChatOutboxDataStore
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.model.Delivery
import com.example.booking_app.feature.chat.domain.model.DiscardResult
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.example.booking_app.feature.chat.domain.toChatError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hàng chờ gửi tin nhắn lưu trong DataStore `chat_outbox` (T020, research R1).
 *
 * - Lưu JSON theo `chatId` để giữ tin chưa gửi sau khi app bị tắt.
 * - Gửi tuần tự từng cuộc trò chuyện để giữ thứ tự tin.
 * - Lắng nghe [NetworkMonitor] để tự gửi lại các tin lỗi tạm thời (mất mạng, quá 60 giây, `BUSY`) dùng cùng `messageId`.
 * - Lỗi không thử lại được (`BLOCKED`, `NOT_PARTICIPANT`, `INVALID_ARGUMENT`...) chuyển `FAILED` ngay và chờ người dùng.
 * - Khi người dùng bấm "Xóa", kiểm tra server trước: nếu tin đã có thì trả [DiscardResult.ALREADY_SENT].
 */
@Singleton
class ChatOutbox @Inject constructor(
    @param:ChatOutboxDataStore private val dataStore: DataStore<Preferences>,
    private val transport: ChatOutboxTransport,
    private val networkMonitor: NetworkMonitor,
    private val clock: AppClock,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val storeMutex = Mutex()
    private val chatMutexes = ConcurrentHashMap<String, Mutex>()
    private val timeoutJobs = ConcurrentHashMap<String, Job>()
    private var monitorJob: Job? = null

    init {
        startNetworkListener()
    }

    /** Quan sát các tin chưa gửi xong của [chatId] theo thứ tự `createdAtLocal`. */
    fun observe(chatId: String): Flow<List<OutboxEntry>> = dataStore.data
        .map { prefs -> decodeMap(prefs)[chatId].orEmpty().sortedBy { it.createdAtLocal } }
        .distinctUntilChanged()

    /** Đưa tin mới vào outbox (`PENDING` ngay lập tức) rồi bắt đầu gửi. */
    suspend fun enqueue(entry: OutboxEntry) {
        val pending = entry.copy(
            status = Delivery.PENDING,
            retryable = true,
            errorCode = null,
        )
        mutateMap { map ->
            val current = map[pending.chatId].orEmpty().filterNot { it.messageId == pending.messageId }
            map[pending.chatId] = current + pending
        }
        scheduleTimeoutIfNeeded(pending)
        scope.launch(ioDispatcher) { drainChat(pending.chatId) }
    }

    /** Gửi lại tin lỗi dùng lại đúng `messageId`. */
    suspend fun retry(chatId: String, messageId: String) {
        var updatedEntry: OutboxEntry? = null
        mutateMap { map ->
            val list = map[chatId] ?: return@mutateMap
            map[chatId] = list.map { entry ->
                if (entry.messageId == messageId) {
                    entry.copy(
                        status = Delivery.PENDING,
                        retryable = true,
                        errorCode = null,
                        createdAtLocal = clock.now(),
                    ).also { updatedEntry = it }
                } else {
                    entry
                }
            }
        }
        updatedEntry?.let { entry ->
            scheduleTimeoutIfNeeded(entry)
            scope.launch(ioDispatcher) { drainChat(chatId) }
        }
    }

    /**
     * Xóa một tin khỏi outbox. Trước khi xóa, kiểm tra `messages/{messageId}` trên server:
     * nếu tin đã lên server (lần gửi trước commit nhưng mất phản hồi) thì bỏ khỏi outbox và trả
     * [DiscardResult.ALREADY_SENT] thay vì xóa ảnh (research R1).
     */
    suspend fun discard(chatId: String, messageId: String): DiscardResult {
        val existing = getEntry(chatId, messageId)
        val existsOnServer = transport.messageExistsOnServer(chatId, messageId)
        removeEntry(chatId, messageId)
        if (existsOnServer) {
            return DiscardResult.ALREADY_SENT
        }
        if (existing != null && (existing.localImageUri != null || existing.imagePath != null)) {
            transport.discardMedia(chatId, messageId)
        }
        return DiscardResult.DISCARDED
    }

    /** Thử gửi lại tất cả tin có thể thử lại (gọi khi có mạng lại hoặc khi khởi động). */
    suspend fun flushRetryable() {
        val map = readMap()
        val chatIdsToDrain = mutableListOf<String>()
        mutateMap { mutable ->
            mutable.forEach { (chatId, list) ->
                var changed = false
                val updated = list.map { entry ->
                    if (entry.retryable && entry.status == Delivery.FAILED) {
                        changed = true
                        entry.copy(status = Delivery.PENDING, errorCode = null)
                    } else {
                        entry
                    }
                }
                if (changed) mutable[chatId] = updated
                if (updated.any { it.retryable && it.status == Delivery.PENDING }) {
                    chatIdsToDrain.add(chatId)
                }
            }
        }
        for (chatId in chatIdsToDrain.distinct()) {
            drainChat(chatId)
        }
    }

    /** Đánh dấu `FAILED` (vẫn `retryable = true`) cho các tin `PENDING` đã quá 60 giây. */
    suspend fun markExpiredAsFailed() {
        val now = clock.now()
        mutateMap { map ->
            map.keys.toList().forEach { chatId ->
                val list = map[chatId].orEmpty()
                val updated = list.map { entry ->
                    if (entry.status == Delivery.PENDING && entry.retryable && now - entry.createdAtLocal >= TIMEOUT_MS) {
                        entry.copy(status = Delivery.FAILED, retryable = true, errorCode = ChatError.Network().code)
                    } else {
                        entry
                    }
                }
                map[chatId] = updated
            }
        }
    }

    /** Dừng các coroutine nền (hữu ích trong unit test). */
    fun close() {
        monitorJob?.cancel()
        monitorJob = null
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
    }

    private fun startNetworkListener() {
        monitorJob = scope.launch(ioDispatcher) {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    flushRetryable()
                }
            }
        }
    }

    private suspend fun drainChat(chatId: String) {
        val mutex = chatMutexes.computeIfAbsent(chatId) { Mutex() }
        mutex.withLock {
            while (true) {
                val next = readMap()[chatId].orEmpty()
                    .sortedBy { it.createdAtLocal }
                    .firstOrNull { it.status == Delivery.PENDING && it.retryable }
                    ?: break

                if (!networkMonitor.isCurrentlyOnline()) {
                    if (clock.now() - next.createdAtLocal >= TIMEOUT_MS) {
                        markEntryFailed(next, retryable = true, errorCode = ChatError.Network().code)
                    } else {
                        scheduleTimeoutIfNeeded(next)
                    }
                    break
                }

                val proceed = processEntry(next)
                if (!proceed) break
            }
        }
    }

    /**
     * Xử lý 1 tin: tải ảnh nếu có → gọi `chat-send`.
     * Trả `true` nếu có thể tiếp tục tin kế tiếp trong cuộc trò chuyện, `false` nếu phải dừng đợi mạng.
     */
    private suspend fun processEntry(initial: OutboxEntry): Boolean {
        var current = initial

        // Bước 1: tải ảnh lên trước nếu có ảnh cục bộ mà chưa có remote imagePath (US4).
        if (current.localImageUri != null && current.imagePath == null) {
            val uploadResult = transport.uploadImage(current.chatId, current.messageId, current.localImageUri!!)
            val uploadedPath = uploadResult.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                val err = throwable.toChatError()
                markEntryFailed(current, retryable = err.isRetryable, errorCode = err.code)
                return !err.isRetryable
            }
            current = current.copy(imagePath = uploadedPath)
            updateEntry(current)
        }

        // Bước 2: gọi `chat-send`, tự thử lại nếu `503 BUSY`.
        var busyRetries = 0
        while (true) {
            // Kiểm tra tin có bị người dùng xóa giữa chừng không.
            if (getEntry(current.chatId, current.messageId) == null) return true

            current = current.copy(attempts = current.attempts + 1)
            updateEntry(current)

            val sendResult = transport.send(current)
            if (sendResult.isSuccess) {
                removeEntry(current.chatId, current.messageId)
                return true
            }

            val err = sendResult.exceptionOrNull()?.toChatError() ?: ChatError.Unknown(null)

            if (err is ChatError.Busy && busyRetries < MAX_BUSY_RETRIES) {
                busyRetries++
                delay(BUSY_RETRY_DELAY_MS * busyRetries)
                continue
            }

            if (!err.isRetryable) {
                // Lỗi không thử lại được (BLOCKED, NOT_PARTICIPANT, INVALID_ARGUMENT, ...): FAILED ngay.
                markEntryFailed(current, retryable = false, errorCode = err.code)
                return true
            }

            // Lỗi do mạng hoặc BUSY quá số lần: kiểm tra mốc 60 giây.
            val elapsed = clock.now() - current.createdAtLocal
            if (elapsed >= TIMEOUT_MS) {
                markEntryFailed(current, retryable = true, errorCode = err.code)
            } else {
                scheduleTimeoutIfNeeded(current)
            }
            return false
        }
    }

    private fun scheduleTimeoutIfNeeded(entry: OutboxEntry) {
        val remaining = TIMEOUT_MS - (clock.now() - entry.createdAtLocal)
        timeoutJobs.remove(entry.messageId)?.cancel()
        timeoutJobs[entry.messageId] = scope.launch(ioDispatcher) {
            if (remaining > 0L) delay(remaining)
            val latest = getEntry(entry.chatId, entry.messageId) ?: return@launch
            if (latest.status == Delivery.PENDING && latest.retryable && clock.now() - latest.createdAtLocal >= TIMEOUT_MS) {
                markEntryFailed(latest, retryable = true, errorCode = ChatError.Network().code, cancelTimeout = false)
            }
            timeoutJobs.remove(entry.messageId)
        }
    }

    private suspend fun markEntryFailed(
        entry: OutboxEntry,
        retryable: Boolean,
        errorCode: String?,
        cancelTimeout: Boolean = true,
    ) {
        if (cancelTimeout) timeoutJobs.remove(entry.messageId)?.cancel()
        updateEntry(entry.copy(status = Delivery.FAILED, retryable = retryable, errorCode = errorCode))
    }

    private suspend fun updateEntry(updated: OutboxEntry) {
        mutateMap { map ->
            val list = map[updated.chatId] ?: return@mutateMap
            if (list.none { it.messageId == updated.messageId }) return@mutateMap
            map[updated.chatId] = list.map { if (it.messageId == updated.messageId) updated else it }
        }
    }

    private suspend fun removeEntry(chatId: String, messageId: String) {
        timeoutJobs.remove(messageId)?.cancel()
        mutateMap { map ->
            val filtered = map[chatId].orEmpty().filterNot { it.messageId == messageId }
            if (filtered.isEmpty()) map.remove(chatId) else map[chatId] = filtered
        }
    }

    private suspend fun getEntry(chatId: String, messageId: String): OutboxEntry? =
        readMap()[chatId]?.firstOrNull { it.messageId == messageId }

    private suspend fun readMap(): Map<String, List<OutboxEntry>> =
        decodeMap(dataStore.data.first())

    private suspend fun mutateMap(block: (MutableMap<String, List<OutboxEntry>>) -> Unit) {
        storeMutex.withLock {
            dataStore.edit { prefs ->
                val map = decodeMap(prefs).toMutableMap()
                block(map)
                prefs[KEY_OUTBOX] = OutboxJson.encodeToString(map)
            }
        }
    }

    private fun decodeMap(prefs: Preferences): Map<String, List<OutboxEntry>> {
        val raw = prefs[KEY_OUTBOX] ?: return emptyMap()
        return runCatching { OutboxJson.decodeFromString<Map<String, List<OutboxEntry>>>(raw) }
            .getOrDefault(emptyMap())
    }

    companion object {
        const val TIMEOUT_MS = 60_000L
        const val MAX_BUSY_RETRIES = 3
        const val BUSY_RETRY_DELAY_MS = 200L
        private val KEY_OUTBOX = stringPreferencesKey("outbox_by_chat")
        private val OutboxJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

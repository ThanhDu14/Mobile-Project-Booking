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
    private val retryJobs = ConcurrentHashMap<String, Job>()
    private val retryAttempts = ConcurrentHashMap<String, Int>()
    private val enqueuedInSession = ConcurrentHashMap.newKeySet<String>()
    private var monitorJob: Job? = null

    init {
        startNetworkListener()
    }

    /** Quan sát các tin chưa gửi xong của [chatId] (thuộc người dùng hiện tại) theo thứ tự `createdAtLocal`. */
    fun observe(chatId: String): Flow<List<OutboxEntry>> = dataStore.data
        .map { prefs ->
            decodeMap(prefs)[chatId].orEmpty()
                .filter(::isOwnedByCurrentUser)
                .sortedBy { it.createdAtLocal }
        }
        .distinctUntilChanged()

    /** Đưa tin mới vào outbox (`PENDING` ngay lập tức) rồi bắt đầu gửi. */
    suspend fun enqueue(entry: OutboxEntry) {
        enqueuedInSession.add(entry.messageId)
        val pending = entry.copy(
            status = Delivery.PENDING,
            retryable = true,
            errorCode = null,
            senderId = entry.senderId ?: transport.currentUid(),
        )
        mutateMap { map ->
            val current = map[pending.chatId].orEmpty().filterNot { it.messageId == pending.messageId }
            map[pending.chatId] = current + pending
        }
        clearRetryState(pending.chatId)
        scheduleTimeoutIfNeeded(pending)
        scope.launch(ioDispatcher) { drainChat(pending.chatId, includeRetryableFailed = false) }
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
            clearRetryState(chatId)
            scheduleTimeoutIfNeeded(entry)
            scope.launch(ioDispatcher) { drainChat(chatId, includeRetryableFailed = false) }
        }
    }

    /**
     * Xóa một tin khỏi outbox. Trước khi xóa, kiểm tra `messages/{messageId}` trên server
     * (khi đã từng thử gửi và đang có mạng): nếu tin đã lên server (lần gửi trước commit nhưng mất
     * phản hồi) thì bỏ khỏi outbox và trả [DiscardResult.ALREADY_SENT] thay vì xóa ảnh (research R1).
     */
    suspend fun discard(chatId: String, messageId: String): DiscardResult {
        val mutex = chatMutexes.computeIfAbsent(chatId) { Mutex() }
        val (existing, existsOnServer) = mutex.withLock {
            val entry = getEntry(chatId, messageId)
            val onServer = if (entry != null && entry.attempts > 0 && networkMonitor.isCurrentlyOnline()) {
                transport.messageExistsOnServer(chatId, messageId)
            } else {
                false
            }
            removeEntry(chatId, messageId)
            entry to onServer
        }
        if (existsOnServer) {
            return DiscardResult.ALREADY_SENT
        }
        if (existing != null && (existing.localImageUri != null || existing.imagePath != null)) {
            scope.launch(ioDispatcher) {
                transport.discardMedia(chatId, messageId)
            }
        }
        return DiscardResult.DISCARDED
    }

    /** Thử gửi lại tất cả tin có thể thử lại (gọi khi có mạng lại hoặc khi khởi động). */
    suspend fun flushRetryable() {
        flushRetryableInternal(excludeMessageIds = emptySet())
    }

    private suspend fun flushRetryableInternal(excludeMessageIds: Set<String>) {
        val map = readMap()
        val chatIdsToDrain = map.entries
            .filter { (_, list) ->
                list.any { entry ->
                    entry.messageId !in excludeMessageIds &&
                        isOwnedByCurrentUser(entry) &&
                        entry.retryable &&
                        (entry.status == Delivery.PENDING || entry.status == Delivery.FAILED)
                }
            }
            .map { it.key }
        for (chatId in chatIdsToDrain) {
            clearRetryState(chatId)
            scope.launch(ioDispatcher) { drainChat(chatId, includeRetryableFailed = true) }
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
                        timeoutJobs.remove(entry.messageId)?.cancel()
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
        cancelAllRetryJobs()
    }

    private fun startNetworkListener() {
        monitorJob = scope.launch(ioDispatcher) {
            restorePendingTimeouts()
            var isInitial = true
            networkMonitor.isOnline.collect { online ->
                val initialEmission = isInitial
                isInitial = false
                if (online) {
                    if (initialEmission) {
                        // Lúc khởi động, chỉ flush tin tồn từ phiên trước; tin vừa enqueue đã tự drainChat.
                        flushRetryableInternal(excludeMessageIds = enqueuedInSession.toSet())
                    } else {
                        flushRetryable()
                    }
                } else {
                    cancelAllRetryJobs()
                }
            }
        }
    }

    private suspend fun restorePendingTimeouts() {
        val map = readMap()
        if (map.isEmpty()) return
        val now = clock.now()
        val hasExpired = map.values.flatten().any {
            it.status == Delivery.PENDING && it.retryable && now - it.createdAtLocal >= TIMEOUT_MS
        }
        if (hasExpired) markExpiredAsFailed()
        readMap().values.flatten()
            .filter { it.status == Delivery.PENDING && it.retryable }
            .forEach { scheduleTimeoutIfNeeded(it) }
    }

    private suspend fun drainChat(chatId: String, includeRetryableFailed: Boolean = false) {
        val mutex = chatMutexes.computeIfAbsent(chatId) { Mutex() }
        mutex.withLock {
            while (true) {
                val next = readMap()[chatId].orEmpty()
                    .sortedBy { it.createdAtLocal }
                    .firstOrNull { entry ->
                        isOwnedByCurrentUser(entry) && entry.retryable &&
                            (entry.status == Delivery.PENDING || (includeRetryableFailed && entry.status == Delivery.FAILED))
                    }
                    ?: break

                if (!networkMonitor.isCurrentlyOnline()) {
                    reconcilePendingTimeouts(chatId)
                    break
                }

                val ready = if (next.status == Delivery.FAILED) {
                    next.copy(status = Delivery.PENDING, errorCode = null).also { updateEntry(it) }
                } else {
                    next
                }

                val proceed = processEntry(ready)
                if (!proceed) {
                    reconcilePendingTimeouts(chatId)
                    break
                }
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
                clearRetryState(current.chatId)
                removeEntry(current.chatId, current.messageId)
                return true
            }

            val sendEx = sendResult.exceptionOrNull()
            if (sendEx is CancellationException) throw sendEx
            val err = sendEx?.toChatError() ?: ChatError.Unknown(null)

            if (err is ChatError.Busy && busyRetries < MAX_BUSY_RETRIES) {
                busyRetries++
                delay(BUSY_RETRY_DELAY_MS * busyRetries)
                continue
            }

            if (!err.isRetryable) {
                // Nếu server báo PHOTO_MISSING thì xóa imagePath để lần "Gửi lại" tải ảnh lại từ đầu.
                val toMark = if (err is ChatError.PhotoMissing) current.copy(imagePath = null) else current
                // Lỗi không thử lại được (BLOCKED, NOT_PARTICIPANT, INVALID_ARGUMENT, ...): FAILED ngay.
                markEntryFailed(toMark, retryable = false, errorCode = err.code)
                return true
            }

            // Lỗi do mạng hoặc BUSY quá số lần: kiểm tra mốc 60 giây.
            val elapsed = clock.now() - current.createdAtLocal
            if (elapsed >= TIMEOUT_MS) {
                clearRetryState(current.chatId)
                markEntryFailed(current, retryable = true, errorCode = err.code)
            } else {
                scheduleTimeoutIfNeeded(current)
                if (networkMonitor.isCurrentlyOnline() && !scheduleBackoffRetry(current.chatId)) {
                    markEntryFailed(current, retryable = true, errorCode = err.code)
                }
            }
            return false
        }
    }

    private fun scheduleBackoffRetry(chatId: String): Boolean {
        val attemptIndex = retryAttempts[chatId] ?: 0
        if (attemptIndex >= RETRY_BACKOFF_DELAYS_MS.size) {
            retryAttempts.remove(chatId)
            return false
        }
        val delayMs = RETRY_BACKOFF_DELAYS_MS[attemptIndex]
        retryAttempts[chatId] = attemptIndex + 1
        retryJobs.remove(chatId)?.cancel()
        retryJobs[chatId] = scope.launch(ioDispatcher) {
            delay(delayMs)
            retryJobs.remove(chatId)
            if (networkMonitor.isCurrentlyOnline()) {
                drainChat(chatId, includeRetryableFailed = false)
            }
        }
        return true
    }

    private suspend fun reconcilePendingTimeouts(chatId: String) {
        val now = clock.now()
        val pendingUnexpired = mutableListOf<OutboxEntry>()
        mutateMap { map ->
            val list = map[chatId] ?: return@mutateMap
            map[chatId] = list.map { entry ->
                if (entry.status == Delivery.PENDING && entry.retryable) {
                    if (now - entry.createdAtLocal >= TIMEOUT_MS) {
                        timeoutJobs.remove(entry.messageId)?.cancel()
                        entry.copy(status = Delivery.FAILED, retryable = true, errorCode = ChatError.Network().code)
                    } else {
                        pendingUnexpired.add(entry)
                        entry
                    }
                } else {
                    entry
                }
            }
        }
        pendingUnexpired.forEach { entry ->
            if (timeoutJobs[entry.messageId]?.isActive != true) {
                scheduleTimeoutIfNeeded(entry)
            }
        }
    }

    private fun isOwnedByCurrentUser(entry: OutboxEntry): Boolean {
        val activeUid = transport.currentUid() ?: return true
        return entry.senderId == null || entry.senderId == activeUid
    }

    private fun clearRetryState(chatId: String) {
        retryAttempts.remove(chatId)
        retryJobs.remove(chatId)?.cancel()
    }

    private fun cancelAllRetryJobs() {
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
        retryAttempts.clear()
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
        var becameEmpty = false
        var removedLocalUri: String? = null
        mutateMap { map ->
            val current = map[chatId].orEmpty()
            removedLocalUri = current.firstOrNull { it.messageId == messageId }?.localImageUri
            val filtered = current.filterNot { it.messageId == messageId }
            if (filtered.isEmpty()) {
                map.remove(chatId)
                becameEmpty = true
            } else {
                map[chatId] = filtered
            }
        }
        if (becameEmpty) clearRetryState(chatId)
        if (!removedLocalUri.isNullOrBlank()) {
            transport.deleteLocalMedia(removedLocalUri)
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
        val RETRY_BACKOFF_DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)
        private val KEY_OUTBOX = stringPreferencesKey("outbox_by_chat")
        private val OutboxJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

package com.example.booking_app.core.media

import com.example.booking_app.core.util.AppClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** Kết quả một lần xin signed URL (T047): map `path -> signedUrl` kèm mốc hết hạn. */
data class SignedUrlBatch(
    val urls: Map<String, String>,
    val expiresAt: Instant?,
)

/**
 * Cache signed URL trong bộ nhớ (spec 090 T047; đặt ở `core/media/` để spec 091 dùng chung).
 *
 * - Trả URL đã cache khi còn hạn (trừ [SAFETY_MARGIN_MS] = 5 phút an toàn).
 * - Gom các path còn thiếu hoặc hết hạn thành từng lô tối đa [MAX_BATCH_SIZE] (40) path để gọi [fetch].
 * - Path nào [fetch] không trả về (ví dụ không có quyền hoặc lỗi mạng) không được cache nên lần sau có thể thử lại.
 */
class SignedUrlCache(
    private val fetch: suspend (List<String>) -> Result<SignedUrlBatch>,
    private val clock: AppClock,
) {
    private data class Entry(val url: String, val expiresAtMs: Long)

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry>()

    /** Trả map `path -> signedUrl` cho [paths]; chỉ gọi server với các path chưa có hoặc sắp hết hạn. */
    suspend fun resolve(paths: Collection<String>): Map<String, String> {
        val requested = paths.asSequence().filter { it.isNotBlank() }.distinct().toList()
        if (requested.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, String>(requested.size)
        val missing = ArrayList<String>()
        val now = clock.now()

        mutex.withLock {
            for (path in requested) {
                val entry = entries[path]
                if (entry != null && isFresh(entry, now)) {
                    result[path] = entry.url
                } else {
                    if (entry != null) entries.remove(path)
                    missing.add(path)
                }
            }
        }

        for (chunk in missing.chunked(MAX_BATCH_SIZE)) {
            val batch = fetch(chunk).getOrNull() ?: continue
            currentCoroutineContext().ensureActive()
            val expiresAtMs = batch.expiresAt?.toEpochMilli() ?: (clock.now() + DEFAULT_TTL_MS)
            mutex.withLock {
                for ((path, url) in batch.urls) {
                    if (url.isNotBlank()) {
                        entries[path] = Entry(url, expiresAtMs)
                        result[path] = url
                    }
                }
            }
        }

        return result
    }

    /** Lấy URL đã cache nếu còn hạn; không gọi mạng. */
    suspend fun peek(path: String): String? = mutex.withLock {
        val entry = entries[path] ?: return null
        if (isFresh(entry, clock.now())) entry.url else null
    }

    /** Xóa cache cho các [paths] đã bị ghi đè để lần đọc sau xin lại URL mới. */
    suspend fun invalidate(paths: Collection<String>) {
        if (paths.isEmpty()) return
        mutex.withLock {
            for (path in paths) entries.remove(path)
        }
    }

    suspend fun clear() = mutex.withLock { entries.clear() }

    private fun isFresh(entry: Entry, nowMs: Long): Boolean = entry.expiresAtMs - SAFETY_MARGIN_MS > nowMs

    companion object {
        const val SAFETY_MARGIN_MS = 5L * 60 * 1000
        const val DEFAULT_TTL_MS = 60L * 60 * 1000
        const val MAX_BATCH_SIZE = 40
    }
}

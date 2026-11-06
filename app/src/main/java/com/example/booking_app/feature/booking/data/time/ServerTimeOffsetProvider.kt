package com.example.booking_app.feature.booking.data.time

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cung cấp thời gian chuẩn server bằng cách bù trừ offset từ Firebase Auth ID token (iat claim).
 * Giải quyết vấn đề clock drift khi đồng hồ thiết bị người dùng chạy nhanh hoặc chậm hơn giờ máy chủ,
 * tránh vi phạm Security Rules kiểm tra holdExpiresAt <= request.time + 5 phút,
 * và hiển thị đúng thời gian đếm ngược thực tế.
 */
@Singleton
class ServerTimeOffsetProvider @Inject constructor(
    private val auth: FirebaseAuth
) {
    companion object {
        const val SYNC_INTERVAL_MS = 60 * 60 * 1000L // 1 giờ
    }

    @Volatile
    private var offsetMillis: Long = 0L

    @Volatile
    private var lastSyncedAtMillis: Long = 0L

    private val syncMutex = Mutex()

    fun getOffsetMillis(): Long = offsetMillis

    fun setOffsetMillis(offset: Long) {
        offsetMillis = offset
    }

    fun getLastSyncedAtMillis(): Long = lastSyncedAtMillis

    fun setLastSyncedAtMillis(time: Long) {
        lastSyncedAtMillis = time
    }

    /**
     * Thời gian hiện tại có bù trừ offset giờ server: System.currentTimeMillis() + offset.
     */
    fun now(): Long = System.currentTimeMillis() + offsetMillis

    /**
     * Đồng bộ offset từ Firebase ID Token `iat` claim hoặc issuedAtTimestamp.
     */
    suspend fun syncOffset(forceRefresh: Boolean = false): Long {
        return try {
            val user = auth.currentUser ?: return offsetMillis
            val localBefore = System.currentTimeMillis()
            val tokenResult = user.getIdToken(forceRefresh).await()
            val localAfter = System.currentTimeMillis()
            val localTime = (localBefore + localAfter) / 2L
            val issuedAtSeconds = tokenResult.claims["iat"] as? Number
                ?: (tokenResult.issuedAtTimestamp.takeIf { it > 0 })
            if (issuedAtSeconds != null) {
                val serverTimeMillis = issuedAtSeconds.toLong() * 1000L
                offsetMillis = serverTimeMillis - localTime
                lastSyncedAtMillis = System.currentTimeMillis()
            }
            offsetMillis
        } catch (e: Exception) {
            // Giữ offset cũ nếu lỗi và không throw
            offsetMillis
        }
    }

    /**
     * Đảm bảo offset đã được đồng bộ ít nhất 1 lần hoặc chưa quá hạn (1 giờ).
     * Chỉ đồng bộ khi chưa từng sync hoặc lần sync cuối đã quá SYNC_INTERVAL_MS.
     * Lỗi thì giữ offset cũ (= 0 lần đầu), không crash, không chặn giữ chỗ.
     */
    suspend fun ensureSynced(forceRefresh: Boolean = false): Long {
        val nowLocal = System.currentTimeMillis()
        if (lastSyncedAtMillis > 0L && !forceRefresh && (nowLocal - lastSyncedAtMillis) < SYNC_INTERVAL_MS) {
            return offsetMillis
        }
        return syncMutex.withLock {
            val checkNow = System.currentTimeMillis()
            if (lastSyncedAtMillis > 0L && !forceRefresh && (checkNow - lastSyncedAtMillis) < SYNC_INTERVAL_MS) {
                return offsetMillis
            }
            try {
                syncOffset(forceRefresh)
            } catch (e: Exception) {
                offsetMillis
            }
        }
    }
}

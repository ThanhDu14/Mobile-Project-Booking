package com.example.booking_app.feature.booking.data.time

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cung cấp thời gian chuẩn server bằng cách bù trừ offset từ Firebase Auth ID token (iat claim).
 * Giải quyết vấn đề clock drift khi đồng hồ thiết bị người dùng chạy nhanh hơn giờ máy chủ,
 * tránh vi phạm Security Rules kiểm tra holdExpiresAt <= request.time + 5 phút.
 */
@Singleton
class ServerTimeOffsetProvider @Inject constructor(
    private val auth: FirebaseAuth
) {
    @Volatile
    private var offsetMillis: Long = 0L

    fun getOffsetMillis(): Long = offsetMillis

    fun setOffsetMillis(offset: Long) {
        offsetMillis = offset
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
            }
            offsetMillis
        } catch (e: Exception) {
            offsetMillis
        }
    }
}

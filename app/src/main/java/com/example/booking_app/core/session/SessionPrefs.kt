package com.example.booking_app.core.session

import kotlinx.coroutines.flow.Flow

/** Người dùng đã bấm "Đăng ký" nhưng `on-signup` chưa chạy xong (research R4). */
data class PendingSignup(val uid: String, val name: String, val termsAt: Long)

/**
 * Dữ liệu phiên lưu trên máy (DataStore `session_prefs`, data-model §3).
 * Không lưu mật khẩu, OTP hay token (FR-024).
 */
interface SessionPrefs {
    /** Đã qua màn hình chào lần đầu. Không xóa khi đăng xuất. */
    val welcomeSeen: Flow<Boolean>
    suspend fun setWelcomeSeen()

    suspend fun pendingSignup(): PendingSignup?
    suspend fun savePendingSignup(pendingSignup: PendingSignup)
    suspend fun clearPendingSignup()

    suspend fun lastVerificationEmailAt(): Long?
    suspend fun setLastVerificationEmailAt(at: Long)

    suspend fun lastPasswordResetAt(): Long?
    suspend fun setLastPasswordResetAt(at: Long)
    suspend fun clearLastPasswordResetAt()

    /** Xóa `pending_signup_*` và `last_verification_email_at`, giữ `welcome_seen`. */
    suspend fun clearOnSignOut()
}

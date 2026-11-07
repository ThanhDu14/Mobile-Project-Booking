package com.example.booking_app.feature.auth.domain.model

import android.os.Parcelable

/**
 * Phiên OTP đang chờ nhập mã (data-model §4, research R8). Firebase không giới hạn thời hạn mã hay số lần sai
 * với số thử nghiệm, nên app tự thực thi 5 phút và 5 lần sai (FR-016). Giữ trong `SavedStateHandle`.
 *
 * [resendToken] là `ForceResendingToken` của Firebase, domain chỉ cần biết nó `Parcelable`.
 */
data class OtpSession(
    val phoneE164: String,
    val verificationId: String,
    val resendToken: Parcelable?,
    /** `elapsedRealtime` lúc gửi mã. */
    val sentAtElapsed: Long,
    val wrongCount: Int = 0,
) {
    val remainingAttempts: Int get() = (MAX_WRONG_ATTEMPTS - wrongCount).coerceAtLeast(0)

    fun isUsable(nowElapsed: Long): Boolean = nowElapsed < sentAtElapsed + VALIDITY_MS && wrongCount < MAX_WRONG_ATTEMPTS

    companion object {
        const val VALIDITY_MS = 300_000L
        const val MAX_WRONG_ATTEMPTS = 5
    }
}

/** Kết quả gửi mã: đã gửi SMS, hoặc máy tự xác minh được (đã đăng nhập luôn). */
sealed interface OtpSendResult {
    data class CodeSent(val verificationId: String, val resendToken: Parcelable?) : OtpSendResult

    data object AutoVerified : OtpSendResult
}

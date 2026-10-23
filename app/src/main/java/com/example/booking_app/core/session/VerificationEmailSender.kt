package com.example.booking_app.core.session

/**
 * Gửi lại email xác minh cho banner ở `core/ui` mà không phải import `feature/auth`.
 * Cài đặt: `ResendVerificationEmailUseCase` (ghi mốc `last_verification_email_at` khi gửi được).
 */
interface VerificationEmailSender {
    /** `true` khi đã gửi. */
    suspend fun send(): Boolean
}

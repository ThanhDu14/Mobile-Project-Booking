package com.example.booking_app.feature.auth.domain.usecase

import android.app.Activity
import android.os.Parcelable
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSendResult
import com.example.booking_app.feature.auth.domain.model.OtpSession
import com.example.booking_app.feature.auth.domain.validation.PhoneRule
import com.example.booking_app.feature.auth.domain.validation.Validated
import javax.inject.Inject

sealed interface SendOtpResult {
    /** Đã gửi SMS: phiên mới, `wrongCount = 0`. */
    data class CodeSent(val session: OtpSession) : SendOtpResult

    /** Máy tự xác minh được, đã đăng nhập; `MainActivity` điều hướng theo `SessionState`. */
    data object SignedIn : SendOtpResult
}

/**
 * Gửi (hoặc gửi lại) mã OTP (US5, research R8). Số được chuẩn hóa E.164 trước khi gửi (FR-017).
 * Số không phải số thử nghiệm trên gói Spark trả `PhoneNotSupported`.
 */
class SendOtpUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    private val clock: AppClock,
) {
    suspend operator fun invoke(activity: Activity, phone: String, resendToken: Parcelable? = null): AuthResult<SendOtpResult> {
        val phoneE164 = when (val v = PhoneRule.validate(phone)) {
            is Validated.Invalid -> return AuthResult.Failure(AuthError.Unknown(IllegalArgumentException(v.error.key)))
            is Validated.Valid -> v.value
        }
        return when (val result = repository.sendOtp(activity, phoneE164, resendToken)) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> when (val sent = result.value) {
                is OtpSendResult.CodeSent -> AuthResult.Success(
                    SendOtpResult.CodeSent(
                        OtpSession(phoneE164, sent.verificationId, sent.resendToken, clock.elapsedRealtime(), wrongCount = 0),
                    ),
                )
                OtpSendResult.AutoVerified -> {
                    sessionManager.refresh(forceTokenRefresh = true)
                    AuthResult.Success(SendOtpResult.SignedIn)
                }
            }
        }
    }
}

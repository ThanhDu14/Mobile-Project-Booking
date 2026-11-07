package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSession
import javax.inject.Inject

sealed interface VerifyOtpResult {
    data object Success : VerifyOtpResult

    /** Mã sai: phiên với `wrongCount` đã tăng 1. */
    data class WrongCode(val session: OtpSession) : VerifyOtpResult

    data class Failure(val error: AuthError) : VerifyOtpResult
}

/**
 * Xác nhận mã OTP (US5, FR-016, research R8). Hết 5 phút hoặc đã sai 5 lần thì báo `CodeExpired` mà không gọi
 * Firebase: người dùng phải "Gửi lại mã". Đúng mã thì làm mới phiên; số mới thì `SessionManager` chuyển sang
 * `NeedsConsent` để qua bước Hoàn tất tài khoản.
 */
class VerifyOtpUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    private val clock: AppClock,
) {
    suspend operator fun invoke(session: OtpSession, code: String): VerifyOtpResult {
        if (!session.isUsable(clock.elapsedRealtime())) return VerifyOtpResult.Failure(AuthError.CodeExpired)
        return when (val result = repository.verifyOtp(session.verificationId, code)) {
            is AuthResult.Success -> {
                sessionManager.refresh(forceTokenRefresh = true)
                VerifyOtpResult.Success
            }
            is AuthResult.Failure ->
                if (result.error == AuthError.InvalidCode) {
                    VerifyOtpResult.WrongCode(session.copy(wrongCount = session.wrongCount + 1))
                } else {
                    VerifyOtpResult.Failure(result.error)
                }
        }
    }
}

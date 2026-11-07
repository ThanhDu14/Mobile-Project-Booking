package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import javax.inject.Inject

/**
 * Gửi email đặt lại mật khẩu (US3, FR-018, research R9). Email có hay chưa có tài khoản đều báo thành công
 * để không lộ email nào đã đăng ký. Gửi xong ghi mốc `last_password_reset_at` cho 60 giây chờ gửi lại.
 */
class SendPasswordResetUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val prefs: SessionPrefs,
    private val clock: AppClock,
) {
    suspend operator fun invoke(email: String): AuthResult<Unit> {
        val result = repository.sendPasswordResetEmail(email.trim().lowercase())
        // Chưa bật Email enumeration protection thì Firebase trả ERROR_USER_NOT_FOUND (đổi thành InvalidCredentials).
        if (result is AuthResult.Failure && result.error != AuthError.InvalidCredentials) return result
        prefs.setLastPasswordResetAt(clock.now())
        return AuthResult.Success(Unit)
    }
}

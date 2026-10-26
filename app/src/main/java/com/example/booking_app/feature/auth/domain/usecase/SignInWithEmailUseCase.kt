package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthResult
import javax.inject.Inject

/**
 * Đăng nhập email/mật khẩu (US2, FR-011). Sai email hay sai mật khẩu đều là `InvalidCredentials`.
 * Xong thì làm mới token; `SessionManager` đổi trạng thái và `MainActivity` điều hướng theo vai trò.
 */
class SignInWithEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
) {
    suspend operator fun invoke(email: String, password: String): AuthResult<Unit> {
        val result = repository.signInWithEmail(email.trim().lowercase(), password)
        if (result is AuthResult.Success) sessionManager.refresh(forceTokenRefresh = true)
        return result
    }
}

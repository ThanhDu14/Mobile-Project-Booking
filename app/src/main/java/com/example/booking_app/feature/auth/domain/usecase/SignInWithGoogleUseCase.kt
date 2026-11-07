package com.example.booking_app.feature.auth.domain.usecase

import android.app.Activity
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.auth.data.AuthErrorMapper
import com.example.booking_app.feature.auth.data.GoogleCredentialDataSource
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthResult
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Đăng nhập Google (US4, research R12). Lần đầu thì `SessionManager` chuyển sang `NeedsConsent` để qua bước
 * Hoàn tất tài khoản; email trùng tài khoản mật khẩu ngoài `@gmail.com` thì trả `NeedsLinking` (R7).
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val google: GoogleCredentialDataSource,
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
) {
    suspend operator fun invoke(activity: Activity): AuthResult<Unit> {
        val idToken = try {
            google.requestIdToken(activity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AuthResult.Failure(AuthErrorMapper.map(e))
        }
        val result = repository.signInWithGoogle(idToken)
        if (result is AuthResult.Success) sessionManager.refresh(forceTokenRefresh = true)
        return result
    }
}

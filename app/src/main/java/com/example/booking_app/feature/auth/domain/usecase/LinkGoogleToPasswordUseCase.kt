package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.di.ApplicationScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.PendingCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import javax.inject.Inject

/**
 * Liên kết Google vào tài khoản mật khẩu cùng email (FR-012, research R7): đăng nhập bằng mật khẩu rồi
 * `linkWithCredential`. Chạy trong scope của app vì đăng nhập xong `MainActivity` có thể đóng màn hình
 * đăng nhập (hủy ViewModel) trước khi liên kết xong.
 */
class LinkGoogleToPasswordUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend operator fun invoke(email: String, password: String, pending: PendingCredential): AuthResult<Unit> =
        scope.async {
            val signIn = repository.signInWithEmail(email.trim().lowercase(), password)
            if (signIn is AuthResult.Failure) return@async signIn
            val link = repository.linkPendingCredential(pending)
            if (link is AuthResult.Failure) return@async link
            sessionManager.refresh(forceTokenRefresh = true)
            AuthResult.Success(Unit)
        }.await()
}

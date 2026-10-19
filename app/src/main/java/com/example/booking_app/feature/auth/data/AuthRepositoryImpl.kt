package com.example.booking_app.feature.auth.data

import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.PendingCredential
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class AuthRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseAuthDataSource,
) : AuthRepository {

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult<String> = runAuth {
        // Lần trước có thể đã tạo xong sau khi app báo hết giờ; thử lại thì đi tiếp, không tạo lại (R13).
        val existingUid = dataSource.currentUid
        if (existingUid != null && dataSource.currentEmail == email) existingUid else dataSource.createUser(email, password)
    }

    override suspend fun signInWithEmail(email: String, password: String) = runAuth {
        dataSource.signIn(email, password)
    }

    override suspend fun sendEmailVerification() = runAuth { dataSource.sendEmailVerification() }

    override suspend fun sendPasswordResetEmail(email: String) = runAuth { dataSource.sendPasswordResetEmail(email) }

    override suspend fun signInWithGoogle(idToken: String) = runAuth { dataSource.signInWithGoogle(idToken) }

    override suspend fun linkPendingCredential(pending: PendingCredential): AuthResult<Unit> {
        val credential = (pending as? FirebasePendingCredential)?.credential
            ?: return AuthResult.Failure(AuthError.Unknown(IllegalArgumentException("Unsupported credential")))
        return runAuth { dataSource.link(credential) }
    }

    private inline fun <T> runAuth(block: () -> T): AuthResult<T> =
        try {
            AuthResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(AuthErrorMapper.map(e))
        }
}

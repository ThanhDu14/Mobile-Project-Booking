package com.example.booking_app.feature.auth.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.PendingCredential
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException

/** Giữ `AuthCredential` của Firebase mà domain không cần biết kiểu cụ thể. */
data class FirebasePendingCredential(val credential: AuthCredential) : PendingCredential

/** Đổi exception của Firebase Auth, Credential Manager và `AppError` thành [AuthError]. */
object AuthErrorMapper {

    private val INVALID_CREDENTIAL_CODES = setOf(
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_WRONG_PASSWORD",
        "ERROR_USER_NOT_FOUND",
        "INVALID_LOGIN_CREDENTIALS",
    )

    fun map(error: Throwable): AuthError = when (error) {
        AppError.Network, AppError.Timeout -> AuthError.Network
        AppError.Locked -> AuthError.AccountLocked
        AppError.RateLimited -> AuthError.TooManyAttempts
        is AppError.Unknown -> error.cause?.let(::map) ?: AuthError.Unknown(error)
        is FirebaseTooManyRequestsException -> AuthError.TooManyAttempts
        is FirebaseAuthException -> mapAuthException(error)
        is GetCredentialCancellationException -> AuthError.GoogleCancelled
        is NoCredentialException -> AuthError.NoGoogleAccount
        else -> AuthError.Unknown(error)
    }

    private fun mapAuthException(error: FirebaseAuthException): AuthError {
        val code = error.errorCode
        val message = error.message.orEmpty()
        return when {
            code in INVALID_CREDENTIAL_CODES || "INVALID_LOGIN_CREDENTIALS" in message -> AuthError.InvalidCredentials
            code == "ERROR_USER_DISABLED" -> AuthError.AccountLocked
            code == "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailInUse
            code == "ERROR_INVALID_VERIFICATION_CODE" -> AuthError.InvalidCode
            code == "ERROR_SESSION_EXPIRED" -> AuthError.CodeExpired
            code == "ERROR_QUOTA_EXCEEDED" || "BILLING_NOT_ENABLED" in code || "BILLING_NOT_ENABLED" in message ->
                AuthError.PhoneNotSupported
            code == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> {
                val collision = error as? FirebaseAuthUserCollisionException
                AuthError.NeedsLinking(
                    email = collision?.email,
                    pendingCredential = collision?.updatedCredential?.let(::FirebasePendingCredential),
                )
            }
            else -> AuthError.Unknown(error)
        }
    }
}

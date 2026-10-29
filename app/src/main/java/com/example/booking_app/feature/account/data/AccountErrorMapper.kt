package com.example.booking_app.feature.account.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/**
 * Đổi lỗi của Firebase Auth, Credential Manager và luồng OTP của 010 thành [AccountError] (research R8, R9).
 * Quá nhiều lần thử trả `AppError.RateLimited` để dùng thông báo chung của 010; lỗi phiên (khóa, bị thu hồi,
 * chưa đăng nhập) giữ nguyên để `SessionManager` xử lý.
 */
object AccountErrorMapper {

    private val WRONG_PASSWORD_CODES = setOf("INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL")
    private val WEAK_PASSWORD_CODES = setOf("PASSWORD_DOES_NOT_MEET_REQUIREMENTS", "ERROR_WEAK_PASSWORD")

    fun map(error: Throwable): Exception = when (error) {
        is AccountError -> error
        AppError.Network, AppError.Timeout, is FirebaseNetworkException -> AccountError.Network
        AppError.RateLimited, is FirebaseTooManyRequestsException -> AppError.RateLimited
        is AppError.Unknown -> error.cause?.let(::map) ?: AccountError.Unknown(error)
        is AppError -> error
        is GetCredentialCancellationException -> AccountError.ReauthCancelled
        // Chỉ lọc tài khoản đã cấp quyền cho app: không có nghĩa là máy không còn tài khoản Google đang đăng nhập.
        is NoCredentialException -> AccountError.ReauthWrongAccount
        is FirebaseAuthException -> mapAuthException(error)
        else -> AccountError.Unknown(error)
    }

    /** Lỗi trả về từ `SendOtpUseCase` của 010. */
    fun map(error: AuthError): Exception = when (error) {
        AuthError.Network -> AccountError.Network
        AuthError.TooManyAttempts -> AppError.RateLimited
        AuthError.GoogleCancelled -> AccountError.ReauthCancelled
        AuthError.AccountLocked -> AppError.Locked
        AuthError.InvalidCode -> AccountError.WrongOtpCode
        AuthError.CodeExpired -> AccountError.OtpExpired
        is AuthError.Unknown -> error.cause?.let(::map) ?: AccountError.Unknown(null)
        else -> AccountError.Unknown(null)
    }

    private fun mapAuthException(error: FirebaseAuthException): Exception {
        val code = error.errorCode
        val message = error.message.orEmpty()
        return when {
            code in WRONG_PASSWORD_CODES || "INVALID_LOGIN_CREDENTIALS" in message -> AccountError.WrongCurrentPassword
            code in WEAK_PASSWORD_CODES || "PASSWORD_DOES_NOT_MEET_REQUIREMENTS" in message -> AccountError.WeakPassword
            "TOO_MANY_ATTEMPTS_TRY_LATER" in code || "TOO_MANY_ATTEMPTS_TRY_LATER" in message -> AppError.RateLimited
            code == "ERROR_USER_MISMATCH" -> AccountError.ReauthWrongAccount
            code == "ERROR_INVALID_VERIFICATION_CODE" -> AccountError.WrongOtpCode
            code == "ERROR_SESSION_EXPIRED" -> AccountError.OtpExpired
            code == "ERROR_REQUIRES_RECENT_LOGIN" -> AccountError.ReauthRequired
            else -> AccountError.Unknown(error)
        }
    }
}

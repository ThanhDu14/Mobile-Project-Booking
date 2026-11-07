package com.example.booking_app.feature.auth.ui

import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.validation.ValidationError

/** Chuỗi tiếng Việt cho lỗi kiểm tra ô nhập ([ValidationError.key] trùng tên chuỗi). */
@StringRes
fun ValidationError.messageRes(): Int = when (this) {
    ValidationError.NAME_LENGTH -> R.string.auth_error_name_length
    ValidationError.EMAIL_FORMAT -> R.string.auth_error_email_format
    ValidationError.PASSWORD_RULE -> R.string.auth_error_password_rule
    ValidationError.PASSWORD_MISMATCH -> R.string.auth_error_password_mismatch
    ValidationError.PHONE_FORMAT -> R.string.auth_error_phone_format
    ValidationError.OTP_FORMAT -> R.string.auth_error_otp_format
    ValidationError.PASSWORD_REQUIRED -> R.string.auth_error_password_required
}

/** Chuỗi tiếng Việt cho từng [AuthError] (T035). */
@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.InvalidCredentials -> R.string.auth_error_invalid_credentials
    AuthError.EmailInUse -> R.string.auth_error_email_in_use
    AuthError.AccountLocked -> R.string.auth_error_account_locked
    AuthError.InvalidCode -> R.string.auth_error_invalid_code
    AuthError.CodeExpired -> R.string.auth_error_code_expired
    AuthError.TooManyAttempts -> R.string.auth_error_too_many_attempts
    AuthError.PhoneNotSupported -> R.string.auth_error_phone_not_supported
    AuthError.NoGoogleAccount -> R.string.auth_error_no_google_account
    AuthError.Network -> R.string.auth_error_network
    AuthError.GoogleCancelled, is AuthError.NeedsLinking, is AuthError.Unknown -> R.string.auth_error_unknown
}

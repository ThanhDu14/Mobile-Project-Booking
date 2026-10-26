package com.example.booking_app.feature.auth.domain.validation

/** Hai dấu tích dưới ô mật khẩu: "Ít nhất 8 ký tự", "Có chữ và số". */
data class PasswordChecks(val len8: Boolean, val letterDigit: Boolean) {
    val isValid: Boolean get() = len8 && letterDigit
}

/** Mật khẩu: ≥ 8 ký tự, có ít nhất một chữ cái và một chữ số (khớp Password policy của Firebase). */
object PasswordRule {
    private const val MIN_LENGTH = 8

    fun check(password: String) = PasswordChecks(
        len8 = password.length >= MIN_LENGTH,
        letterDigit = password.any(Char::isLetter) && password.any(Char::isDigit),
    )

    fun validate(password: String): Validated<String> =
        if (check(password).isValid) Validated.Valid(password) else Validated.Invalid(ValidationError.PASSWORD_RULE)
}

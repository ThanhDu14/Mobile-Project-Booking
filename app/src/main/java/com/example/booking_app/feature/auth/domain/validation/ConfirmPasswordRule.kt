package com.example.booking_app.feature.auth.domain.validation

object ConfirmPasswordRule {
    fun validate(password: String, confirm: String): Validated<String> =
        if (confirm == password) Validated.Valid(confirm) else Validated.Invalid(ValidationError.PASSWORD_MISMATCH)
}

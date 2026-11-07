package com.example.booking_app.feature.auth.domain.validation

object OtpRule {
    private val OTP = Regex("^\\d{6}$")

    fun validate(input: String): Validated<String> =
        if (OTP.matches(input)) Validated.Valid(input) else Validated.Invalid(ValidationError.OTP_FORMAT)
}

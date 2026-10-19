package com.example.booking_app.feature.auth.domain.validation

/** Email: `trim().lowercase()` khớp mẫu tương đương `Patterns.EMAIL_ADDRESS` (để test được trên JVM). */
object EmailRule {
    private val EMAIL_ADDRESS = Regex(
        "[a-zA-Z0-9+._%\\-]{1,256}" +
            "@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+",
    )

    fun validate(input: String): Validated<String> {
        val email = input.trim().lowercase()
        return if (EMAIL_ADDRESS.matches(email)) Validated.Valid(email) else Validated.Invalid(ValidationError.EMAIL_FORMAT)
    }
}

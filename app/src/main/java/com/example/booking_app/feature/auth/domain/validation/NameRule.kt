package com.example.booking_app.feature.auth.domain.validation

/** Họ tên: sau khi `trim` dài 2–50 ký tự (FR-004). */
object NameRule {
    private val LENGTH = 2..50

    fun validate(input: String): Validated<String> {
        val name = input.trim()
        return if (name.length in LENGTH) Validated.Valid(name) else Validated.Invalid(ValidationError.NAME_LENGTH)
    }
}

package com.example.booking_app.feature.auth.domain.validation

/** Số di động Việt Nam đầu 3, 5, 7, 8, 9; chuẩn hóa thành E.164 `+84…` cho Firebase (FR-017). */
object PhoneRule {
    private val VN_MOBILE = Regex("^(0|\\+84)([35789]\\d{8})$")

    fun validate(input: String): Validated<String> {
        val match = VN_MOBILE.matchEntire(input.filterNot(Char::isWhitespace))
            ?: return Validated.Invalid(ValidationError.PHONE_FORMAT)
        return Validated.Valid("+84" + match.groupValues[2])
    }
}

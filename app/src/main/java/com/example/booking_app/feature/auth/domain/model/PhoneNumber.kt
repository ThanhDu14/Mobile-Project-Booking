package com.example.booking_app.feature.auth.domain.model

/** Số di động Việt Nam đã chuẩn hóa E.164 `+84…` (FR-017, `PhoneRule`). */
@JvmInline
value class PhoneNumber(val e164: String) {

    /** Dạng che bớt cho màn hình OTP: `+84 9•• ••• 678`. */
    fun masked(): String {
        val national = e164.removePrefix(COUNTRY_CODE)
        if (national.length < 4) return e164
        return "$COUNTRY_CODE ${national.first()}•• ••• ${national.takeLast(3)}"
    }

    private companion object {
        const val COUNTRY_CODE = "+84"
    }
}

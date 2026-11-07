package com.example.booking_app.feature.courtdetail.domain.model

/** Số điện thoại hiển thị (research R9). */
sealed interface PhoneDisplay {
    /** Bấm để mở trình gọi điện với [e164]. */
    data class Dialable(val e164: String, val display: String) : PhoneDisplay

    /** Không hợp lệ (ví dụ số bị che của dữ liệu demo): chỉ hiện và nhấn giữ để sao chép. */
    data class DisplayOnly(val raw: String) : PhoneDisplay

    /** Số đang hiển thị, dùng khi sao chép. */
    val text: String
        get() = when (this) {
            is Dialable -> display
            is DisplayOnly -> raw
        }

    companion object {
        private val VALID = Regex("""^(0|\+84)\d{9,10}$""")

        /**
         * Bỏ khoảng trắng và dấu chấm rồi khớp `^(0|\+84)\d{9,10}$`: di động 10 chữ số "0901 234 567", số bàn
         * 11 chữ số "028 3812 3456". Không khớp → [DisplayOnly]; rỗng hoặc `null` → `null` (ẩn thẻ).
         */
        fun of(raw: String?): PhoneDisplay? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val compact = trimmed.filterNot { it.isWhitespace() || it == '.' }
            if (!VALID.matches(compact)) return DisplayOnly(trimmed)
            val subscriber = compact.removePrefix("+84").removePrefix("0")
            val national = "0$subscriber"
            val display = if (national.length == 10) {
                "${national.substring(0, 4)} ${national.substring(4, 7)} ${national.substring(7)}"
            } else {
                "${national.substring(0, 3)} ${national.substring(3, 7)} ${national.substring(7)}"
            }
            return Dialable("+84$subscriber", display)
        }
    }
}

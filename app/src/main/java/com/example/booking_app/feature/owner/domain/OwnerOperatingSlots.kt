package com.example.booking_app.feature.owner.domain

/** Whole canonical 30-minute slots inside the existing venue hours. No midnight wrapping. */
object OwnerOperatingSlots {
    fun startMinutes(openTime: String, closeTime: String): List<Int> {
        fun minutes(value: String): Int {
            require(Regex("(?:[01][0-9]|2[0-3])[0-5][0-9]").matches(value)) { "Giờ hoạt động không hợp lệ" }
            return value.take(2).toInt() * 60 + value.takeLast(2).toInt()
        }
        val open = minutes(openTime)
        val close = minutes(closeTime)
        require(open < close) { "Giờ đóng cửa phải sau giờ mở cửa" }
        val first = ((open + 29) / 30) * 30
        return (first until close step 30).filter { it + 30 <= close }
    }
}

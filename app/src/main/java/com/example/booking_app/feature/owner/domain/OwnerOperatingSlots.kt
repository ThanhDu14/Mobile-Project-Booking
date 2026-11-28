package com.example.booking_app.feature.owner.domain

/** Whole canonical 30-minute slots inside the existing venue hours. No midnight wrapping; "2400" closes at end of day. */
object OwnerOperatingSlots {
    fun startMinutes(openTime: String, closeTime: String): List<Int> {
        fun minutes(value: String): Int {
            require(Regex("(?:[01][0-9]|2[0-3])[0-5][0-9]").matches(value)) { "Giờ hoạt động không hợp lệ" }
            return value.take(2).toInt() * 60 + value.takeLast(2).toInt()
        }
        val open = minutes(openTime)
        // "2400" chỉ hợp lệ ở giờ đóng cửa: hết ngày (bảng giá cũng dùng 24:00 làm mốc cuối).
        val close = if (closeTime == END_OF_DAY) 24 * 60 else minutes(closeTime)
        require(open < close) { "Giờ đóng cửa phải sau giờ mở cửa" }
        val first = ((open + 29) / 30) * 30
        return (first until close step 30).filter { it + 30 <= close }
    }

    private const val END_OF_DAY = "2400"
}

package com.example.booking_app.core.pricing

/**
 * Một dòng bảng giá `venues/{id}/priceRules/{id}` (spec 020 research R5). Tiền là `Long` VND, không dùng `Double`.
 * [daysOfWeek] theo [DayConvention.CURRENT]; [endTime] có thể là `"2400"`.
 */
data class PriceRule(
    val id: String,
    val daysOfWeek: List<Int>,
    val startTime: String,
    val endTime: String,
    val pricePerHour: Long,
    val label: String,
) {
    val pricePerSlot: Long get() = pricePerHour / 2
}

package com.example.booking_app.feature.booking.domain.model

data class PriceRule(
    val id: String,
    val daysOfWeek: List<Int>,
    val startTime: String,
    val endTime: String,
    val pricePerHour: Long,
    val label: String
) {
    val pricePerSlot: Long get() = pricePerHour / 2

    companion object {
        // Convention: ISO (1 = Monday ... 7 = Sunday).
        // Centralized convention pending final team confirmation (PR #41 seed script uses ISO).
        const val DAY_MONDAY = 1
        const val DAY_TUESDAY = 2
        const val DAY_WEDNESDAY = 3
        const val DAY_THURSDAY = 4
        const val DAY_FRIDAY = 5
        const val DAY_SATURDAY = 6
        const val DAY_SUNDAY = 7
    }
}

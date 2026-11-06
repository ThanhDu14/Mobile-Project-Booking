package com.example.booking_app.feature.booking.domain.model

data class RecurringConfig(
    val venueId: String,
    val courtId: String,
    val daysOfWeek: List<Int>, // ISO: 1 = T2 (Monday) ... 7 = CN (Sunday)
    val startTime: String,     // HHmm, e.g. "1800"
    val endTime: String,       // HHmm, e.g. "2000"
    val totalWeeks: Int,       // 4..12
    val startNextWeek: Boolean, // false = current week, true = next week
    val slotCount: Int = calculateSlotCount(startTime, endTime)
) {
    val preferredCourtId: String get() = courtId
    val numberOfWeeks: Int get() = totalWeeks
    val isValidSlotRange: Boolean get() = slotCount in 2..6
    val isValidWeeks: Boolean get() = totalWeeks in 4..12

    companion object {
        fun calculateSlotCount(startTime: String, endTime: String): Int {
            if (startTime.length < 4 || endTime.length < 4) return 0
            val startMin = startTime.substring(0, 2).toInt() * 60 + startTime.substring(2, 4).toInt()
            val endMin = endTime.substring(0, 2).toInt() * 60 + endTime.substring(2, 4).toInt()
            return (endMin - startMin) / 30
        }
    }
}

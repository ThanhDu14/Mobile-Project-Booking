package com.example.booking_app.core.location

import kotlin.math.roundToLong

/** "850 m" / "1,2 km" (spec 020 FR-013): làm tròn tới 10 m trước, từ 1.000 m hiện km một chữ số thập phân. */
object DistanceFormatter {

    fun format(meters: Double): String {
        val rounded = (meters / 10).roundToLong() * 10
        if (rounded < 1000) return "$rounded m"
        val tenths = (rounded / 100.0).roundToLong()
        return "${tenths / 10},${tenths % 10} km"
    }
}

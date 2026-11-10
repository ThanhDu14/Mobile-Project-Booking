package com.example.booking_app.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Điểm tổng quan của một cơ sở, tính từ document `venues` (spec 030 research R3). Chi tiết sân (030) và danh sách
 * đánh giá (070) cùng dùng [fromVenue] để ra cùng một số (SC-006). Tiêu chí `null` khi chưa có đánh giá hoặc thiếu
 * `ratingSums` thì giao diện ẩn thanh tiêu chí đó.
 */
data class VenueRatingSummary(
    val average: Double,
    val count: Int,
    val courtAvg: Double?,
    val cleanlinessAvg: Double?,
    val serviceAvg: Double?,
) {
    companion object {
        const val KEY_COURT = "court"
        const val KEY_CLEANLINESS = "cleanliness"
        const val KEY_SERVICE = "service"

        val EMPTY = VenueRatingSummary(0.0, 0, null, null, null)

        fun fromVenue(ratingAvg: Double?, ratingCount: Long?, ratingSums: Map<String, Long>?): VenueRatingSummary {
            val count = (ratingCount ?: 0L).coerceAtLeast(0L)
            fun avg(key: String): Double? {
                if (count == 0L) return null
                val sum = ratingSums?.get(key) ?: return null
                return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).toDouble()
            }
            return VenueRatingSummary(
                average = ratingAvg ?: 0.0,
                count = count.toInt(),
                courtAvg = avg(KEY_COURT),
                cleanlinessAvg = avg(KEY_CLEANLINESS),
                serviceAvg = avg(KEY_SERVICE),
            )
        }
    }
}

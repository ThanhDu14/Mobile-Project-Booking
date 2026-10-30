package com.example.booking_app.feature.review.domain.model

/** Tổng quan điểm của một cơ sở, tính từ `ratingSums` và `ratingCount` của `venues`. */
data class VenueRatingSummary(
    val average: Double,
    val count: Int,
    val courtAvg: Double,
    val cleanlinessAvg: Double,
    val serviceAvg: Double,
)

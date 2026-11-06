package com.example.booking_app.feature.review.domain.model

import java.time.Instant

/**
 * Đơn được đánh giá, đọc từ `bookings/{bookingId}` để hiện thẻ đầu màn hình viết đánh giá
 * (tên sân, ngày giờ, tên sân con) và tính [com.example.booking_app.feature.review.domain.ReviewEligibility].
 */
data class ReviewTarget(
    val bookingId: String,
    val venueId: String,
    val venueName: String,
    val venuePhotoUrl: String?,
    val startAt: Instant?,
    val endAt: Instant?,
    val courtNames: List<String>,
    val status: String?,
    val completedAt: Instant?,
)

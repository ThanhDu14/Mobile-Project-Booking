package com.example.booking_app.feature.review.data.dto

import java.time.Instant

/** Document `reviews/{reviewId}` (data-model mục 1), đã tách khỏi `DocumentSnapshot` để test mapper trên JVM. */
data class ReviewDto(
    val id: String,
    val venueId: String,
    val userId: String?,
    val displayName: String?,
    val avatarPath: String?,
    val authorDeleted: Boolean,
    val court: Int,
    val cleanliness: Int,
    val service: Int,
    val overall: Double,
    val comment: String,
    val photoPaths: List<String>,
    val ownerReplyText: String?,
    val ownerRepliedAt: Instant?,
    val ownerReplyEditedAt: Instant?,
    val status: String?,
    val completedAt: Instant?,
    val editedAt: Instant?,
    /** `null` khi chưa có server timestamp (vừa ghi, đọc từ cache). */
    val createdAt: Instant?,
)

/** Các trường tổng điểm của `venues/{venueId}` (data-model mục 3). [ratingSums] thiếu ở dữ liệu cũ. */
data class VenueRatingFieldsDto(
    val ratingAvg: Double?,
    val ratingCount: Long?,
    val ratingSums: Map<String, Long>?,
)

/** `bookings/{bookingId}` (đặc tả CSDL mục 4.5), chỉ các trường màn hình viết đánh giá cần. */
data class ReviewBookingDto(
    val id: String,
    val venueId: String,
    val venueName: String?,
    val venuePhotoUrl: String?,
    val courtNames: List<String>,
    val startAt: Instant?,
    val endAt: Instant?,
    val status: String?,
    val completedAt: Instant?,
)

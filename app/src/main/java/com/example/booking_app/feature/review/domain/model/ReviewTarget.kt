package com.example.booking_app.feature.review.domain.model

import java.time.Instant

/**
 * Đơn được đánh giá, đọc từ `bookings/{bookingId}` để hiện thẻ đầu màn hình viết đánh giá
 * (tên sân, ngày giờ, tên sân con) và tính [com.example.booking_app.feature.review.domain.ReviewEligibility].
 */
data class ReviewTarget(
    /** Id đơn; `null` khi đánh giá buổi vãng lai (có [sessionId]). */
    val bookingId: String?,
    val venueId: String,
    val venueName: String,
    val venuePhotoUrl: String?,
    val startAt: Instant?,
    val endAt: Instant?,
    val courtNames: List<String>,
    val status: String?,
    val completedAt: Instant?,
    /** Id buổi vãng lai (`dropInSessions/{sessionId}`); `null` khi đánh giá đơn đặt sân. */
    val sessionId: String? = null,
    /**
     * Id document `reviews/...` của đối tượng này: `bookingId`, hoặc `{sessionId}_{uid}` với buổi vãng lai.
     * Cũng là thư mục ảnh `{uid}/{reviewId}/`.
     */
    val reviewId: String = bookingId.orEmpty(),
) {
    val isDropIn: Boolean get() = sessionId != null
}

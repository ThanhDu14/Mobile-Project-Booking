package com.example.booking_app.feature.review.domain

import com.example.booking_app.feature.review.domain.model.Review
import java.time.Duration
import java.time.Instant

/**
 * Nút trên chi tiết đơn (spec 060): [CAN_WRITE] → "Đánh giá", [CAN_VIEW] → "Xem đánh giá của bạn",
 * [EXPIRED] → "Đã hết hạn đánh giá", [NOT_ELIGIBLE] → không hiện gì.
 */
enum class ReviewEligibility {
    CAN_WRITE,
    CAN_VIEW,
    EXPIRED,
    NOT_ELIGIBLE,
    ;

    companion object {
        /** Hạn viết và sửa đánh giá tính từ `completedAt` (FR-001, FR-007). */
        val WINDOW: Duration = Duration.ofDays(7)

        private const val STATUS_COMPLETED = "COMPLETED"

        /**
         * Khớp kiểm tra của Edge Function: còn viết được khi `now <= completedAt + 7 ngày`.
         * [bookingStatus] là `bookings.status` dạng chuỗi để spec 060 truyền thẳng giá trị Firestore.
         */
        fun of(bookingStatus: String?, completedAt: Instant?, existingReview: Review?, now: Instant): ReviewEligibility =
            when {
                existingReview != null -> CAN_VIEW
                bookingStatus != STATUS_COMPLETED || completedAt == null -> NOT_ELIGIBLE
                now.isAfter(completedAt.plus(WINDOW)) -> EXPIRED
                else -> CAN_WRITE
            }

        fun canEditUntil(completedAt: Instant): Instant = completedAt.plus(WINDOW)
    }
}

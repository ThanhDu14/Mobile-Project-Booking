package com.example.booking_app.feature.review.domain

import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.domain.model.SubmittedReview
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import kotlinx.coroutines.flow.Flow

/**
 * Đánh giá sân (contracts/ui-screens.md §2). Đọc trực tiếp Firestore, ghi qua Edge Function `review`.
 * Hàm `suspend` ném [ReviewException]; `Flow` kết thúc bằng [ReviewException] khi lỗi.
 *
 * Nhóm khác chỉ dùng [observeSummary] (spec 030) và [observeMyReview] (spec 060). Các hàm của
 * US2, US4, US5, US6 (`update`, `delete`, `reply`, `discardPhotos`, danh sách) thêm khi làm story đó.
 */
interface ReviewRepository {

    /** Điểm tổng quan của cơ sở; cơ sở chưa có đánh giá thì `count == 0`, mọi điểm bằng 0. */
    fun observeSummary(venueId: String): Flow<VenueRatingSummary>

    /** Đánh giá của người dùng hiện tại cho đơn [bookingId] (kể cả bị ẩn); `null` khi chưa đánh giá. */
    fun observeMyReview(bookingId: String): Flow<Review?>

    /** Thông tin đơn để hiện thẻ đầu màn hình viết đánh giá. */
    suspend fun getReviewTarget(bookingId: String): ReviewTarget

    /**
     * Gửi đánh giá. Gửi lại cùng nội dung (thử lại sau khi mất mạng) trả về đánh giá đã có, không tạo
     * thêm (FR-002).
     */
    suspend fun create(
        bookingId: String,
        ratings: Ratings,
        comment: String,
        photoPaths: List<String> = emptyList(),
    ): SubmittedReview
}

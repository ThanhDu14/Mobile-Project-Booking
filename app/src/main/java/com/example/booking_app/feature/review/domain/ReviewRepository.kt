package com.example.booking_app.feature.review.domain

import com.example.booking_app.feature.review.domain.model.OwnerReply
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
 * Nhóm khác chỉ dùng [observeSummary] (spec 030) và [observeMyReview] (spec 060); các hàm còn lại chỉ
 * dùng trong `feature/review`.
 */
interface ReviewRepository {

    /** Điểm tổng quan của cơ sở; cơ sở chưa có đánh giá thì `count == 0`, mọi điểm bằng 0. */
    fun observeSummary(venueId: String): Flow<VenueRatingSummary>

    /** Đánh giá của người dùng hiện tại cho đơn [bookingId] (kể cả bị ẩn); `null` khi chưa đánh giá. */
    fun observeMyReview(bookingId: String): Flow<Review?>

    /**
     * Đánh giá đang hiển thị của một cơ sở, mới nhất trước (US6). [limit] tăng mỗi lần tải thêm
     * [PAGE_SIZE]; [stars] lọc theo `starBucket` (1–5), [withPhotos] chỉ lấy đánh giá có ảnh (FR-013).
     */
    fun observeVenueReviews(venueId: String, stars: Int?, withPhotos: Boolean, limit: Int): Flow<List<Review>>

    /**
     * Đánh giá đang hiển thị của các cơ sở [venueIds] (≤ 30, giới hạn `in` của Firestore) cho chủ sân (US4);
     * [unrepliedOnly] chỉ lấy đánh giá chưa có phản hồi (FR-020).
     */
    fun observeOwnerReviews(venueIds: List<String>, unrepliedOnly: Boolean, limit: Int): Flow<List<Review>>

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

    /** Sửa đánh giá của mình trong hạn 7 ngày từ khi đơn hoàn thành (US5, FR-007). */
    suspend fun update(reviewId: String, ratings: Ratings, comment: String, photoPaths: List<String>): SubmittedReview

    /** Xóa đánh giá của mình, bất cứ lúc nào (US5, FR-008). */
    suspend fun delete(reviewId: String)

    /** Chủ sân tạo hoặc sửa phản hồi (US4); không có thao tác xóa phản hồi. */
    suspend fun reply(reviewId: String, text: String): OwnerReply

    /** Xóa ảnh đã tải lên mà chưa gắn vào đánh giá đã lưu (bỏ ảnh, bỏ đánh giá đang viết). Trả số ảnh đã xóa. */
    suspend fun discardPhotos(bookingId: String, photoPaths: List<String>): Int

    companion object {
        const val PAGE_SIZE = 20

        /** Giới hạn của truy vấn `whereIn` trên Firestore. */
        const val MAX_OWNER_VENUES = 30
    }
}

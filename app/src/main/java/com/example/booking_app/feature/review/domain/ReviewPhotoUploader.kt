package com.example.booking_app.feature.review.domain

/**
 * Tải ảnh đánh giá lên bucket `review-photos` (US2, research R4). Cài đặt ở
 * [com.example.booking_app.feature.review.data.SupabaseReviewPhotoUploader].
 */
interface ReviewPhotoUploader {
    /**
     * Thu nhỏ, nén rồi tải [localUri] lên `{uid}/{bookingId}/{uuid}.jpg`; trả path đã tải.
     * Ném [ReviewPhotoTooLargeException] khi ảnh sau nén vẫn > 2 MB, [ReviewException] khi lỗi khác.
     */
    suspend fun upload(bookingId: String, localUri: String): String

    companion object {
        const val MAX_PHOTOS = 3
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}

class ReviewPhotoTooLargeException : Exception("Ảnh lớn hơn 2 MB sau khi nén")

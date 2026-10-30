package com.example.booking_app.feature.review.domain.model

/** Trạng thái màn hình viết đánh giá. Tiêu chí chưa chấm là `null`. */
data class ReviewDraft(
    val bookingId: String,
    val court: Int? = null,
    val cleanliness: Int? = null,
    val service: Int? = null,
    val comment: String = "",
    val photos: List<DraftPhoto> = emptyList(),
)

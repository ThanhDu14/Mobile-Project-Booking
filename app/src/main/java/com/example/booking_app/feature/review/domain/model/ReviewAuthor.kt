package com.example.booking_app.feature.review.domain.model

/** Người viết đánh giá. [isDeleted] thì UI hiện "Người dùng đã xóa" (research R9). */
data class ReviewAuthor(
    val displayName: String?,
    val avatarUrl: String?,
    val isDeleted: Boolean,
)

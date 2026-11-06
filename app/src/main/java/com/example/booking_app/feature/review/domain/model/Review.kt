package com.example.booking_app.feature.review.domain.model

import java.time.Instant

data class Review(
    val id: String,
    val venueId: String,
    val author: ReviewAuthor,
    val ratings: Ratings,
    val overall: Double,
    val comment: String,
    val photoUrls: List<String>,
    val ownerReply: OwnerReply?,
    val isHidden: Boolean,
    val isEdited: Boolean,
    val createdAt: Instant,
    val canEditUntil: Instant,
    /** Path trong bucket `review-photos`, cùng thứ tự [photoUrls]; dùng khi sửa đánh giá (US5). */
    val photoPaths: List<String> = emptyList(),
)

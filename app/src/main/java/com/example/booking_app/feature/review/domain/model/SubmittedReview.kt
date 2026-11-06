package com.example.booking_app.feature.review.domain.model

import java.time.Instant

/** Kết quả `create` của Edge Function `review`. */
data class SubmittedReview(
    val id: String,
    val overall: Double,
    val canEditUntil: Instant,
)

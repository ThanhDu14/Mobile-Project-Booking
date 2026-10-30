package com.example.booking_app.feature.review.domain.model

/** Điểm 3 tiêu chí, mỗi tiêu chí 1–5 sao (data-model mục 6). */
data class Ratings(
    val court: Int,
    val cleanliness: Int,
    val service: Int,
)

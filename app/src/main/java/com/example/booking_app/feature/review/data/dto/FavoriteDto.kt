package com.example.booking_app.feature.review.data.dto

import java.time.Instant

/** Document `users/{uid}/favorites/{venueId}` (data-model mục 2). */
data class FavoriteDto(
    val venueId: String,
    val name: String,
    val photoPath: String?,
    val address: String,
    /** `null` khi ghi offline chưa có server timestamp. */
    val createdAt: Instant?,
)

/** Các trường của `venues/{venueId}` mà danh sách yêu thích cần. [exists] = false khi cơ sở đã bị xóa hẳn. */
data class FavoriteVenueStateDto(
    val exists: Boolean,
    val status: String? = null,
    val name: String? = null,
    val photoPath: String? = null,
    val address: String? = null,
    val ratingAvg: Double? = null,
)

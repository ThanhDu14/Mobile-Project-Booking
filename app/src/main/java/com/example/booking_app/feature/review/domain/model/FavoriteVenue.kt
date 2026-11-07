package com.example.booking_app.feature.review.domain.model

/** Một sân trong danh sách yêu thích. [ratingAvg] là `null` khi không đọc được cơ sở. */
data class FavoriteVenue(
    val venueId: String,
    val name: String,
    val photoUrl: String?,
    val address: String,
    val ratingAvg: Double?,
    val availability: Availability,
) {
    /** Trạng thái hiện tại của cơ sở (FR-024, research R6). */
    enum class Availability {
        ACTIVE,

        /** Cơ sở `HIDDEN`, `LOCKED` hoặc `DELETED`: "Sân tạm ngừng hoạt động". */
        SUSPENDED,

        /** Không còn document `venues/{venueId}`: "Sân không còn tồn tại". */
        GONE,
    }
}

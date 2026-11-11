package com.example.booking_app.feature.search.domain.model

/** Một thẻ sân ở Trang chủ (spec 020 FR-015, FR-029). Giá `null` là chưa có bảng giá ("Liên hệ"). */
data class HomeVenue(
    val id: String,
    val name: String,
    val districtName: String?,
    val photoUrl: String?,
    val minPricePerHour: Long?,
    val ratingAvg: Double,
    val ratingCount: Int,
)

/** Cách sắp xếp ở Trang chủ (FR-011). "Gần nhất" chưa làm ở lần này. */
enum class HomeSort { RATING, PRICE }

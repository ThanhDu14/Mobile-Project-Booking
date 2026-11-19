package com.example.booking_app.feature.search.domain.model

import com.example.booking_app.core.location.VenueArea
import com.example.booking_app.core.schedule.BookingSearchWindow

/** Một thẻ sân ở Trang chủ (spec 020 FR-015, FR-029). Giá `null` là chưa có bảng giá ("Liên hệ"). */
data class HomeVenue(
    val id: String,
    val name: String,
    val districtName: String?,
    val photoUrl: String?,
    val minPricePerHour: Long?,
    val ratingAvg: Double,
    val ratingCount: Int,
    val distanceMeters: Double? = null,
    val windowPricePerHour: Long? = null,
    val window: BookingSearchWindow? = null,
    /** Tọa độ cơ sở (`venues.location`), `null` khi thiếu hoặc không hợp lệ: không hiện trên bản đồ (021 FR-002). */
    val position: LatLng? = null,
)

/** Một điểm trên bản đồ (độ). Không dùng `GeoPoint` của Firebase để domain và test không phụ thuộc SDK. */
data class LatLng(val lat: Double, val lng: Double) {
    /** Nằm trong vùng Việt Nam; tọa độ nhập sai như (90, 90) hay (0, 0) coi như chưa có vị trí. */
    val isValid: Boolean get() = VenueArea.contains(lat, lng)
}

/** Cách sắp xếp ở Trang chủ (FR-011). "Gần nhất" chưa làm ở lần này. */
enum class HomeSort { RATING, PRICE }

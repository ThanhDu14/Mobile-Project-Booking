package com.example.booking_app.feature.courtdetail.domain.model

import com.example.booking_app.core.catalog.Amenity
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.model.VenueRatingSummary

data class LatLng(val lat: Double, val lng: Double)

/** Bảng trạng thái ở research R10. */
enum class VenueAvailability {
    ACTIVE,

    /** `HIDDEN` hoặc `LOCKED`, người đã đăng nhập vẫn xem được. */
    SUSPENDED,

    /** `DELETED` hoặc không tồn tại. */
    GONE,

    /** Khách bị `PERMISSION_DENIED` (cơ sở tạm ngừng). Chỉ `ObserveVenueDetailUseCase` quyết định. */
    SUSPENDED_GUEST,
}

/** Danh mục quận và tiện ích để gắn tên vào cơ sở. `null` ở nơi dùng = tải danh mục lỗi. */
data class VenueCatalog(val districts: List<District>, val amenities: List<Amenity>)

/**
 * Thông tin một cơ sở đã gộp danh mục và giải URL ảnh (data-model §2.2).
 * [amenities] `null` khi danh mục tải lỗi (mục "Tiện ích" hiện "Thử lại"); [phone] là chuỗi gốc, đổi sang
 * `PhoneDisplay` ở ViewModel.
 */
data class VenueDetail(
    val id: String,
    val name: String,
    val address: String,
    val districtName: String?,
    val location: LatLng?,
    val openTime: String,
    val closeTime: String,
    val phone: String?,
    val amenities: List<Amenity>?,
    val photoUrls: List<String>,
    val coverPhotoPath: String?,
    val ownerId: String,
    val rating: VenueRatingSummary,
    val availability: VenueAvailability,
    val isFromCache: Boolean,
)

/** Kết quả lắng nghe `venues/{id}` (contract navigation §3, tasks T022). */
sealed interface VenueResult {
    data class Found(val venue: VenueDetail) : VenueResult
    data object Gone : VenueResult
    data object PermissionDenied : VenueResult
    data class Failed(val error: com.example.booking_app.core.network.AppError) : VenueResult
}

package com.example.booking_app.feature.courtdetail.ui

import com.example.booking_app.core.catalog.Amenity
import com.example.booking_app.core.model.VenueRatingSummary
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.feature.courtdetail.domain.model.CourtSummary
import com.example.booking_app.feature.courtdetail.domain.model.LatLng
import com.example.booking_app.feature.courtdetail.domain.model.OpeningStatus
import com.example.booking_app.feature.courtdetail.domain.model.PendingAction
import com.example.booking_app.feature.courtdetail.domain.model.PhoneDisplay
import com.example.booking_app.feature.courtdetail.domain.model.PriceDayGroup
import com.example.booking_app.feature.courtdetail.domain.model.ReviewPreview
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import com.example.booking_app.feature.courtdetail.domain.model.SelectedWindowPrice
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability

/** Trạng thái riêng của từng mục (research R12): lỗi một mục không chặn mục khác. */
sealed interface Section<out T> {
    data object Loading : Section<Nothing>
    data class Content<T>(val data: T) : Section<T>
    data object Empty : Section<Nothing>
    data class Error(val error: AppError) : Section<Nothing>
}

/** Mục có nút "Thử lại". */
enum class SectionId { HEADER, COURTS, AMENITIES, PRICES, REVIEWS }

data class HeaderContent(
    val name: String,
    val address: String,
    val districtName: String?,
    val rating: VenueRatingSummary,
    val opening: OpeningStatus?,
    val openTime: TimeOfDay?,
    val closeTime: TimeOfDay?,
    val photoUrls: List<String>,
    val suspended: Boolean,
)

data class PriceContent(val groups: List<PriceDayGroup>, val selected: SelectedWindowPrice?)

data class ReviewsContent(val summary: VenueRatingSummary, val reviews: List<ReviewPreview>)

/** Giá ở thanh dưới: "120.000 đ/giờ" + khung đã chọn, "từ X đ/giờ", "Liên hệ". */
sealed interface BottomPrice {
    data object Loading : BottomPrice
    data class ForWindow(val perHour: Long, val window: SearchWindow) : BottomPrice
    data class From(val perHour: Long) : BottomPrice
    data object Contact : BottomPrice
}

sealed interface BottomBarState {
    data class Book(val price: BottomPrice) : BottomBarState

    /** Cơ sở tạm ngừng: ẩn chat và "Đặt sân", hiện ghi chú (FR-021). */
    data object Suspended : BottomBarState
    data object Hidden : BottomBarState
}

/** data-model §2.5. `availability == null` là đang tải lần đầu. */
data class CourtDetailUiState(
    val availability: VenueAvailability? = null,
    val header: Section<HeaderContent> = Section.Loading,
    val prices: Section<PriceContent> = Section.Loading,
    val courts: Section<CourtSummary> = Section.Loading,
    val amenities: Section<List<Amenity>> = Section.Loading,
    val reviews: Section<ReviewsContent> = Section.Loading,
    val map: Section<LatLng> = Section.Loading,
    val phone: PhoneDisplay? = null,
    val distanceText: String? = null,
    /** `null` = khách hoặc đang tải. */
    val isFavorite: Boolean? = null,
    val isOffline: Boolean = false,
    val bottomBar: BottomBarState = BottomBarState.Hidden,
    val window: SearchWindow? = null,
    val photoIndex: Int = 0,
)

enum class CourtDetailMessage {
    OWN_VENUE,
    SUSPENDED,
    PHONE_COPIED,
    FAVORITE_ADDED,
    FAVORITE_REMOVED,
    FAVORITE_FAILED,
    COMING_SOON,
}

/** Sự kiện một lần (data-model §2.5). */
sealed interface CourtDetailEffect {
    data class OpenBookingGrid(val venueId: String, val window: SearchWindow?) : CourtDetailEffect
    data class OpenReviews(val venueId: String, val venueName: String) : CourtDetailEffect
    data class OpenChat(val venueId: String) : CourtDetailEffect
    data class OpenPhotoViewer(val urls: List<String>, val index: Int) : CourtDetailEffect
    data class Dial(val e164: String) : CourtDetailEffect
    data class CopyPhone(val text: String) : CourtDetailEffect
    data class OpenDirections(val lat: Double, val lng: Double, val name: String) : CourtDetailEffect
    data class ShowLoginPrompt(val action: PendingAction) : CourtDetailEffect
    data object OpenLogin : CourtDetailEffect
    data class Snackbar(val message: CourtDetailMessage) : CourtDetailEffect
}

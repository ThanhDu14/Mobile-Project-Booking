package com.example.booking_app.feature.courtdetail.domain.usecase

import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.feature.courtdetail.domain.VenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface Bookable {
    data object Yes : Bookable
    data class No(val availability: VenueAvailability) : Bookable
}

/**
 * Đọc lại trạng thái từ server trước khi mở lưới giờ (FR-023, research R10). Mất mạng hoặc quá 15 giây thì dùng
 * trạng thái mới nhất từ listener. Chỉ để trải nghiệm tốt hơn: `create-booking` (B) phải tự từ chối ở server.
 */
class CheckVenueBookableUseCase @Inject constructor(
    private val repository: VenueDetailRepository,
) {
    suspend operator fun invoke(venueId: String, latestKnown: VenueAvailability): Bookable {
        val server = withTimeoutOrNull(DEFAULT_TIMEOUT_MS) { repository.fetchStatusFromServer(venueId) }
        val status = server ?: latestKnown
        return if (status == VenueAvailability.ACTIVE) Bookable.Yes else Bookable.No(status)
    }
}

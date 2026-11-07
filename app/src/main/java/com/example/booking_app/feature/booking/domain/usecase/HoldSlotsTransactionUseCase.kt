package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import javax.inject.Inject

class HoldSlotsTransactionUseCase @Inject constructor(
    private val repository: BookingRepository
) {

    suspend operator fun invoke(
        venueId: String,
        courtId: String,
        date: String,
        slotIds: List<String>,
        userId: String,
        requestId: String
    ): Result<HoldResult> {
        if (slotIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("Danh sách slot không được rỗng"))
        }
        return repository.holdSlotsAtomic(
            venueId = venueId,
            courtId = courtId,
            date = date,
            slotIds = slotIds,
            userId = userId,
            requestId = requestId
        )
    }
}

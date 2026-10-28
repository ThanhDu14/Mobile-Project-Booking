package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import javax.inject.Inject

class ReleaseHoldUseCase @Inject constructor(
    private val repository: BookingRepository
) {

    suspend operator fun invoke(
        venueId: String,
        slotIds: List<String>,
        userId: String
    ): Result<Unit> {
        if (slotIds.isEmpty()) return Result.success(Unit)
        return repository.releaseHoldSlots(
            venueId = venueId,
            slotIds = slotIds,
            userId = userId
        )
    }
}

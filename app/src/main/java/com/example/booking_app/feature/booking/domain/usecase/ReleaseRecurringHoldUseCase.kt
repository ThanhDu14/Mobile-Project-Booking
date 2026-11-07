package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import javax.inject.Inject

class ReleaseRecurringHoldUseCase @Inject constructor(
    private val repository: RecurringBookingRepository
) {
    suspend operator fun invoke(
        recurringGroupId: String,
        slotIds: List<String>,
        userId: String
    ): Result<Unit> {
        return repository.releaseRecurringHold(recurringGroupId, slotIds, userId)
    }
}

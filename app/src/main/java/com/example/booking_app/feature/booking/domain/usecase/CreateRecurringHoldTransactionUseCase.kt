package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import javax.inject.Inject

class CreateRecurringHoldTransactionUseCase @Inject constructor(
    private val repository: RecurringBookingRepository
) {
    suspend operator fun invoke(
        venueId: String,
        config: RecurringConfig,
        sessions: List<RecurringSession>,
        userId: String,
        requestId: String
    ): Result<RecurringHoldResult> {
        val validSessions = sessions.filter { !it.isExcluded }
        if (validSessions.size < 4) {
            return Result.failure(
                IllegalArgumentException("Cần tối thiểu 4 buổi hợp lệ để thực hiện giữ chỗ cố định")
            )
        }
        return repository.holdRecurringBookingTransaction(
            venueId = venueId,
            config = config,
            sessions = validSessions,
            userId = userId,
            requestId = requestId
        )
    }
}

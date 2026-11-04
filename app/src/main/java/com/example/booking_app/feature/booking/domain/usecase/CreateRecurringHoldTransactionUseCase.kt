package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import javax.inject.Inject

class CreateRecurringHoldTransactionUseCase @Inject constructor(
    private val repository: RecurringBookingRepository
) {
    companion object {
        const val MAX_TRANSACTION_WRITES = 500
    }

    suspend operator fun invoke(
        venueId: String,
        config: RecurringConfig,
        sessions: List<RecurringSession>,
        userId: String,
        requestId: String
    ): Result<RecurringHoldResult> {
        val validSessions = sessions.filter { !it.isExcluded && it.status != RecurringSessionStatus.MUST_SKIP }
        if (validSessions.size < 4) {
            return Result.failure(
                IllegalArgumentException("Cần tối thiểu 4 buổi hợp lệ để thực hiện giữ chỗ cố định")
            )
        }

        // P0-6: Guard Firestore Transaction 500 writes limit
        val totalWrites = 1 + validSessions.size + validSessions.sumOf { it.slotIds.size }
        if (totalWrites > MAX_TRANSACTION_WRITES) {
            return Result.failure(
                IllegalArgumentException("Số khung giờ đặt vượt quá giới hạn 500 của hệ thống, vui lòng giảm số tuần hoặc số thứ")
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

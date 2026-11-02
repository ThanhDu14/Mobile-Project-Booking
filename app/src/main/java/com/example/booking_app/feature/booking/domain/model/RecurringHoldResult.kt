package com.example.booking_app.feature.booking.domain.model

import java.time.LocalDate

sealed interface RecurringHoldResult {
    data class Success(
        val recurringGroupId: String,
        val holdExpiresAtMillis: Long
    ) : RecurringHoldResult

    data class Conflict(
        val conflictedSessionDates: List<LocalDate>
    ) : RecurringHoldResult
}

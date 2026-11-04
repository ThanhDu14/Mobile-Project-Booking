package com.example.booking_app.feature.booking.ui.state

import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.model.RecurringSummary

data class BookingRecurringUiState(
    val isLoading: Boolean = false,
    val isCheckingAvailability: Boolean = false,
    val isHolding: Boolean = false,
    val venueId: String = "",
    val venueName: String = "",
    val availableCourts: List<Court> = emptyList(),
    val priceRules: List<PriceRule> = emptyList(),
    val config: RecurringConfig? = null,
    val sessions: List<RecurringSession> = emptyList(),
    val summary: RecurringSummary? = null,
    val hasConflicts: Boolean = false,
    val holdCountdownSeconds: Int = 300,
    val activeRecurringGroupId: String? = null,
    val errorMessage: String? = null
) {
    val conflictingSessions: List<RecurringSession>
        get() = sessions.filter {
            it.status == RecurringSessionStatus.SUGGEST_CHANGE_COURT ||
                it.status == RecurringSessionStatus.MUST_SKIP ||
                it.status == RecurringSessionStatus.EXCLUDED
        }

    val validSessions: List<RecurringSession>
        get() = sessions.filter { !it.isExcluded && it.status != RecurringSessionStatus.MUST_SKIP }

    val hasUnresolvedConflicts: Boolean
        get() = sessions.any { it.status == RecurringSessionStatus.SUGGEST_CHANGE_COURT }

    val canProceed: Boolean
        get() = validSessions.size >= 4 && !hasUnresolvedConflicts && !isCheckingAvailability && !isLoading && !isHolding
}

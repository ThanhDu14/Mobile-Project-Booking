package com.example.booking_app.feature.booking.domain.model

import java.time.LocalDate

data class RecurringConflict(
    val sessionDate: LocalDate,
    val originalCourtId: String,
    val suggestedCourts: List<Court> = emptyList(),
    val mustSkip: Boolean = false
)

package com.example.booking_app.feature.booking.domain.repository

import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.TimeSlot

interface RecurringBookingRepository {
    suspend fun isRecurringAccepted(venueId: String): Result<Boolean>
    suspend fun getActiveCourts(venueId: String): Result<List<Court>>
    suspend fun getPriceRules(venueId: String): Result<List<PriceRule>>
    suspend fun getBusySlotsForDates(venueId: String, dates: List<String>): Result<List<TimeSlot>>
    suspend fun holdRecurringBookingTransaction(
        venueId: String,
        config: RecurringConfig,
        sessions: List<RecurringSession>,
        userId: String,
        requestId: String
    ): Result<RecurringHoldResult>
    suspend fun releaseRecurringHold(
        recurringGroupId: String,
        slotIds: List<String>,
        userId: String
    ): Result<Unit>
}

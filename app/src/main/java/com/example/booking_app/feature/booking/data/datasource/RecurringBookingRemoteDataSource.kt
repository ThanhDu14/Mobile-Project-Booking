package com.example.booking_app.feature.booking.data.datasource

import com.example.booking_app.feature.booking.data.model.BookingSessionDto
import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.RecurringGroupDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult

interface RecurringBookingRemoteDataSource {
    suspend fun isRecurringAccepted(venueId: String): Boolean
    suspend fun getVenueName(venueId: String): String
    suspend fun getActiveCourts(venueId: String): List<CourtDto>
    suspend fun getPriceRules(venueId: String): List<PriceRuleDto>
    suspend fun getBusySlotsForDates(venueId: String, dates: List<String>): List<SlotDto>
    suspend fun holdRecurringBookingTransaction(
        venueId: String,
        recurringGroup: RecurringGroupDto,
        sessions: List<BookingSessionDto>,
        slotIds: List<String>,
        userId: String
    ): RecurringHoldResult
    suspend fun releaseRecurringHold(
        recurringGroupId: String,
        slotIds: List<String>,
        userId: String
    )
}

package com.example.booking_app.feature.booking.data.datasource

import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.HoldResult
import kotlinx.coroutines.flow.Flow

interface BookingRemoteDataSource {
    suspend fun getVenueOperatingHours(venueId: String): Pair<String, String>

    suspend fun getActiveCourts(venueId: String): List<CourtDto>

    suspend fun getPriceRules(venueId: String): List<PriceRuleDto>

    fun getBusySlotsRealtime(venueId: String, date: String): Flow<List<SlotDto>>

    suspend fun holdSlotsAtomic(
        venueId: String,
        courtId: String,
        date: String,
        slotIds: List<String>,
        userId: String,
        requestId: String
    ): HoldResult

    suspend fun releaseHoldSlots(
        venueId: String,
        slotIds: List<String>,
        userId: String
    )
}

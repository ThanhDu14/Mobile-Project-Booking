package com.example.booking_app.feature.booking.domain.repository

import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.model.VenueInfo
import kotlinx.coroutines.flow.Flow

interface BookingRepository {
    suspend fun getVenueOperatingHours(venueId: String): Result<Pair<String, String>>

    suspend fun getVenueInfo(venueId: String): Result<VenueInfo>

    suspend fun getActiveCourts(venueId: String): Result<List<Court>>

    suspend fun getPriceRules(venueId: String): Result<List<PriceRule>>

    fun getBusySlotsRealtime(venueId: String, date: String): Flow<List<TimeSlot>>

    suspend fun holdSlotsAtomic(
        venueId: String,
        courtId: String = "",
        date: String,
        slotIds: List<String>,
        userId: String,
        requestId: String
    ): Result<HoldResult>

    suspend fun releaseHoldSlots(
        venueId: String,
        slotIds: List<String>,
        userId: String
    ): Result<Unit>
}

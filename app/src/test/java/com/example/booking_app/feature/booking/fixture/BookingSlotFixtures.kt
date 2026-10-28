package com.example.booking_app.feature.booking.fixture

import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.google.firebase.Timestamp
import java.util.Date

object BookingSlotFixtures {

    const val VENUE_ID = "demo-venue-01"
    const val COURT_ID_1 = "court-01"
    const val COURT_ID_2 = "court-02"
    const val DATE = "20261027"

    // Standard test price rates (Decision #2: Normal 80k/h -> 40k/slot, Peak 150k/h -> 75k/slot)
    const val NORMAL_PRICE_PER_HOUR = 80_000L
    const val NORMAL_PRICE_PER_SLOT = 40_000L

    const val PEAK_PRICE_PER_HOUR = 150_000L
    const val PEAK_PRICE_PER_SLOT = 75_000L

    fun createCourtDto(
        id: String = COURT_ID_1,
        name: String = "Sân 1",
        surfaceType: String = "PU",
        isActive: Boolean = true,
        sortOrder: Int = 1
    ) = CourtDto(
        id = id,
        name = name,
        surfaceType = surfaceType,
        isActive = isActive,
        sortOrder = sortOrder
    )

    fun createCourt(
        id: String = COURT_ID_1,
        name: String = "Sân 1",
        surfaceType: String = "PU",
        isActive: Boolean = true,
        sortOrder: Int = 1
    ) = Court(
        id = id,
        name = name,
        surfaceType = surfaceType,
        isActive = isActive,
        sortOrder = sortOrder
    )

    fun createNormalPriceRuleDto(
        id: String = "rule-normal",
        daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5), // Mon-Fri
        startTime: String = "0600",
        endTime: String = "1700",
        pricePerHour: Long = NORMAL_PRICE_PER_HOUR,
        label: String = "NORMAL"
    ) = PriceRuleDto(
        id = id,
        daysOfWeek = daysOfWeek,
        startTime = startTime,
        endTime = endTime,
        pricePerHour = pricePerHour,
        label = label
    )

    fun createPeakPriceRuleDto(
        id: String = "rule-peak",
        daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5), // Mon-Fri
        startTime: String = "1700",
        endTime: String = "2200",
        pricePerHour: Long = PEAK_PRICE_PER_HOUR,
        label: String = "PEAK"
    ) = PriceRuleDto(
        id = id,
        daysOfWeek = daysOfWeek,
        startTime = startTime,
        endTime = endTime,
        pricePerHour = pricePerHour,
        label = label
    )

    fun createNormalPriceRule(
        id: String = "rule-normal",
        daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5),
        startTime: String = "0600",
        endTime: String = "1700",
        pricePerHour: Long = NORMAL_PRICE_PER_HOUR,
        label: String = "NORMAL"
    ) = PriceRule(
        id = id,
        daysOfWeek = daysOfWeek,
        startTime = startTime,
        endTime = endTime,
        pricePerHour = pricePerHour,
        label = label
    )

    fun createPeakPriceRule(
        id: String = "rule-peak",
        daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5),
        startTime: String = "1700",
        endTime: String = "2200",
        pricePerHour: Long = PEAK_PRICE_PER_HOUR,
        label: String = "PEAK"
    ) = PriceRule(
        id = id,
        daysOfWeek = daysOfWeek,
        startTime = startTime,
        endTime = endTime,
        pricePerHour = pricePerHour,
        label = label
    )

    fun createSlotDto(
        id: String = "${COURT_ID_1}_${DATE}_0600",
        venueId: String = VENUE_ID,
        courtId: String = COURT_ID_1,
        date: String = DATE,
        startTime: String = "0600",
        endTime: String = "0630",
        status: String = "AVAILABLE",
        blockReason: String? = null,
        holdExpiresAt: Timestamp? = null,
        holdBy: String? = null,
        bookingId: String? = null
    ) = SlotDto(
        id = id,
        venueId = venueId,
        courtId = courtId,
        date = date,
        startTime = startTime,
        endTime = endTime,
        status = status,
        blockReason = blockReason,
        holdExpiresAt = holdExpiresAt,
        holdBy = holdBy,
        bookingId = bookingId
    )

    fun createTimeSlot(
        slotId: String = "${COURT_ID_1}_${DATE}_0600",
        courtId: String = COURT_ID_1,
        date: String = DATE,
        startTime: String = "0600",
        endTime: String = "0630",
        price: Long = NORMAL_PRICE_PER_SLOT,
        status: SlotStatus = SlotStatus.AVAILABLE,
        blockReason: String? = null,
        holdExpiresAtMillis: Long? = null
    ) = TimeSlot(
        slotId = slotId,
        courtId = courtId,
        date = date,
        startTime = startTime,
        endTime = endTime,
        price = price,
        status = status,
        blockReason = blockReason,
        holdExpiresAtMillis = holdExpiresAtMillis
    )
}

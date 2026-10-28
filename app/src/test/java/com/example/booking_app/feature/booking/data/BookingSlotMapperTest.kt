package com.example.booking_app.feature.booking.data

import com.example.booking_app.feature.booking.data.mapper.BookingSlotMapper
import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class BookingSlotMapperTest {

    @Test
    fun courtMapping_bidirectional_preservesData() {
        val dto = CourtDto(id = "court-01", name = "Sân 1", surfaceType = "PU", isActive = true, sortOrder = 1)
        val domain = BookingSlotMapper.toDomain(dto)

        assertEquals("court-01", domain.id)
        assertEquals("Sân 1", domain.name)
        assertEquals("PU", domain.surfaceType)
        assertTrue(domain.isActive)
        assertEquals(1, domain.sortOrder)

        val backToDto = BookingSlotMapper.toDto(domain)
        assertEquals(dto, backToDto)
    }

    @Test
    fun priceRuleMapping_bidirectional_preservesData() {
        val dto = PriceRuleDto(
            id = "rule-01",
            daysOfWeek = listOf(1, 2, 3),
            startTime = "0600",
            endTime = "1700",
            pricePerHour = 80_000L,
            label = "NORMAL"
        )
        val domain = BookingSlotMapper.toDomain(dto)

        assertEquals("rule-01", domain.id)
        assertEquals(listOf(1, 2, 3), domain.daysOfWeek)
        assertEquals("0600", domain.startTime)
        assertEquals("1700", domain.endTime)
        assertEquals(80_000L, domain.pricePerHour)
        assertEquals(40_000L, domain.pricePerSlot)
        assertEquals("NORMAL", domain.label)

        val backToDto = BookingSlotMapper.toDto(domain)
        assertEquals(dto, backToDto)
    }

    @Test
    fun slotMapping_expiredHeldSlot_becomesAvailable() {
        // Expired hold: holdExpiresAt is 10 minutes in the past
        val pastTimestamp = Timestamp(Date(System.currentTimeMillis() - 10 * 60 * 1000L))
        val dto = SlotDto(
            id = "court-01_20261027_0600",
            courtId = "court-01",
            date = "20261027",
            startTime = "0600",
            endTime = "0630",
            status = "HELD",
            holdExpiresAt = pastTimestamp
        )

        val domain = BookingSlotMapper.toDomain(dto, calculatedPrice = 40_000L)
        assertEquals(SlotStatus.AVAILABLE, domain.status)
    }

    @Test
    fun slotMapping_activeHeldSlot_remainsHeld() {
        // Active hold: holdExpiresAt is 5 minutes in future
        val futureTimestamp = Timestamp(Date(System.currentTimeMillis() + 5 * 60 * 1000L))
        val dto = SlotDto(
            id = "court-01_20261027_0600",
            courtId = "court-01",
            date = "20261027",
            startTime = "0600",
            endTime = "0630",
            status = "HELD",
            holdExpiresAt = futureTimestamp
        )

        val domain = BookingSlotMapper.toDomain(dto, calculatedPrice = 40_000L)
        assertEquals(SlotStatus.HELD, domain.status)
        assertEquals(futureTimestamp.toDate().time, domain.holdExpiresAtMillis)
    }
}

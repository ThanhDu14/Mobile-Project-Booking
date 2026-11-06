package com.example.booking_app.feature.owner.domain.statistics

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerOccupancyFormulaTest {
    @Test
    fun calculatesOccupancyExcludingBlockedSlotsAndDeduplicating() {
        val t1 = Instant.parse("2026-11-06T10:00:00Z")
        val t2 = Instant.parse("2026-11-06T10:30:00Z")

        val slots = listOf(
            StatisticsSlotRecord("s1", "c1", status = "BOOKED", bookingStatus = "CONFIRMED", startAt = t1, endAt = t2),
            StatisticsSlotRecord("s2", "c1", status = "BOOKED", bookingStatus = "COMPLETED", startAt = t1, endAt = t2),
            StatisticsSlotRecord("s3", "c1", status = "OPEN", startAt = t1, endAt = t2),
            StatisticsSlotRecord("s4", "c1", status = "OPEN", startAt = t1, endAt = t2),
            StatisticsSlotRecord("s5", "c1", status = "BLOCKED", blockReason = "MAINTENANCE", startAt = t1, endAt = t2),
            // Duplicate of s1 that should be deduplicated:
            StatisticsSlotRecord("s1", "c1", status = "BOOKED", bookingStatus = "CONFIRMED", startAt = t1, endAt = t2),
        )

        // Denominator: s1, s2, s3, s4 (4 available slots, s5 is BLOCKED, duplicate s1 ignored)
        // Numerator: s1, s2 (2 occupied slots)
        // Expected: 2 / 4 * 100% = 50.0%
        val occupancy = OwnerStatisticsCalculator.calculateOccupancy(slots)
        assertEquals(50.0, occupancy, 0.001)
    }

    @Test
    fun calculatesPeakHoursRankedByOccupiedMinutesInLocalHour() {
        // In UTC: 10:00 UTC = 17:00 in Asia/Ho_Chi_Minh (+7)
        val t17 = Instant.parse("2026-11-06T10:00:00Z")
        val t17_30 = Instant.parse("2026-11-06T10:30:00Z")
        val t18 = Instant.parse("2026-11-06T11:00:00Z")
        val t18_30 = Instant.parse("2026-11-06T11:30:00Z")

        val slots = listOf(
            // Hour 17: two slots (60 mins)
            StatisticsSlotRecord("s1", "c1", status = "BOOKED", bookingStatus = "CONFIRMED", startAt = t17, endAt = t17_30),
            StatisticsSlotRecord("s2", "c2", status = "BOOKED", bookingStatus = "CONFIRMED", startAt = t17, endAt = t17_30),
            // Hour 18: one slot (30 mins)
            StatisticsSlotRecord("s3", "c1", status = "BOOKED", bookingStatus = "CONFIRMED", startAt = t18, endAt = t18_30),
        )

        val peakHours = OwnerStatisticsCalculator.calculatePeakHours(slots)

        assertEquals(2, peakHours.size)
        // First peak hour should be hour 17 with 60 mins
        assertEquals(17, peakHours[0].localHour)
        assertEquals(60, peakHours[0].occupiedDurationMinutes)
        // Second should be hour 18 with 30 mins
        assertEquals(18, peakHours[1].localHour)
        assertEquals(30, peakHours[1].occupiedDurationMinutes)
    }

    @Test
    fun emptySlotsReturnsZeroOccupancyAndEmptyPeakHours() {
        assertEquals(0.0, OwnerStatisticsCalculator.calculateOccupancy(emptyList()), 0.001)
        assertTrue(OwnerStatisticsCalculator.calculatePeakHours(emptyList()).isEmpty())
    }
}

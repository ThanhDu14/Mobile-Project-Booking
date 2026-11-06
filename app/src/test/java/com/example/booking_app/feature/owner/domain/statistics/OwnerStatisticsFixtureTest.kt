package com.example.booking_app.feature.owner.domain.statistics

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerStatisticsFixtureTest {
    @Test
    fun computesCompleteStatisticsResultFromFixtures() {
        val now = Instant.parse("2026-11-06T12:00:00Z")

        val bookings = listOf(
            StatisticsBookingRecord("b1", status = "CONFIRMED", paymentStatus = "PAID", totalVnd = 500_000L),
            StatisticsBookingRecord("b2", status = "CONFIRMED", paymentStatus = "DEPOSITED", depositAmountVnd = 100_000L),
            StatisticsBookingRecord("b3", status = "CONFIRMED", paymentStatus = "REFUNDED", refundAmountVnd = 80_000L),
            StatisticsBookingRecord("b4", status = "REJECTED", paymentStatus = "PAID", totalVnd = 300_000L),
        )

        val slots = listOf(
            StatisticsSlotRecord("s1", "c1", status = "BOOKED", bookingStatus = "CONFIRMED", startAt = Instant.parse("2026-11-06T10:00:00Z"), endAt = Instant.parse("2026-11-06T10:30:00Z")),
            StatisticsSlotRecord("s2", "c2", status = "BOOKED", bookingStatus = "COMPLETED", startAt = Instant.parse("2026-11-06T10:00:00Z"), endAt = Instant.parse("2026-11-06T10:30:00Z")),
            StatisticsSlotRecord("s3", "c1", status = "OPEN", startAt = Instant.parse("2026-11-06T11:00:00Z"), endAt = Instant.parse("2026-11-06T11:30:00Z")),
            StatisticsSlotRecord("s4", "c2", status = "OPEN", startAt = Instant.parse("2026-11-06T11:00:00Z"), endAt = Instant.parse("2026-11-06T11:30:00Z")),
        )

        val revenue = OwnerStatisticsCalculator.calculateRevenue(bookings)
        val occupancy = OwnerStatisticsCalculator.calculateOccupancy(slots)
        val peakHours = OwnerStatisticsCalculator.calculatePeakHours(slots)

        val result = OwnerStatisticsResult(
            revenue = revenue,
            occupancyPercent = occupancy,
            peakHours = peakHours,
            computedAt = now,
            isPartialPeriod = false,
        )

        assertEquals(500_000L, result.revenue.paidVnd)
        assertEquals(100_000L, result.revenue.depositedVnd)
        assertEquals(1, result.revenue.refundedBookingCount)
        assertEquals(80_000L, result.revenue.refundedVnd)
        assertEquals(50.0, result.occupancyPercent, 0.001)
        assertEquals(1, result.peakHours.size)
        assertEquals(17, result.peakHours[0].localHour) // 10:00 UTC = 17:00 ICT
        assertEquals(60, result.peakHours[0].occupiedDurationMinutes)
        assertFalse(result.isPartialPeriod)
    }

    @Test
    fun handlesPartialPeriodFlagForOngoingDay() {
        val result = OwnerStatisticsResult(
            revenue = OwnerRevenueBreakdown(0L, 0L, 0, 0L),
            occupancyPercent = 0.0,
            peakHours = emptyList(),
            computedAt = Instant.now(),
            isPartialPeriod = true,
        )
        assertTrue(result.isPartialPeriod)
    }
}

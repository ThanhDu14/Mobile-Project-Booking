package com.example.booking_app.feature.owner.domain.statistics

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class OwnerStatisticsRegressionTest {
    @Test fun cancelledRefundRemainsVisibleWithoutCountingItAsCollectedRevenue() {
        val result = OwnerStatisticsCalculator.calculateRevenue(listOf(
            StatisticsBookingRecord("cancelled", "CANCELLED", "REFUNDED", totalVnd = 100000, refundAmountVnd = 25000),
            StatisticsBookingRecord("rejected", "REJECTED", "PAID", totalVnd = 100000)))
        assertEquals(0L, result.paidVnd)
        assertEquals(0L, result.depositedVnd)
        assertEquals(1, result.refundedBookingCount)
        assertEquals(25000L, result.refundedVnd)
    }
    @Test fun excludesUncollectedAtVenueDepositsAndStaleCancelledSlots() {
        val revenue=OwnerStatisticsCalculator.calculateRevenue(listOf(
            StatisticsBookingRecord("uncollected","CONFIRMED","DEPOSITED","AT_VENUE",100000,30000),
            StatisticsBookingRecord("collected","CONFIRMED","PAID","AT_VENUE",100000),
        ))
        assertEquals(0L,revenue.depositedVnd)
        assertEquals(100000L,revenue.paidVnd)
        val slots=listOf(StatisticsSlotRecord("stale","c","BOOKED",startAt=Instant.parse("2026-11-15T10:00:00Z"),
            endAt=Instant.parse("2026-11-15T10:30:00Z"),bookingStatus="CANCELLED"))
        assertEquals(0.0,OwnerStatisticsCalculator.calculateOccupancy(slots),0.0)
    }

    @Test fun splitsPeakMinutesAcrossLocalHourBoundariesAndExcludesBlockedSlots() {
        val from=Instant.parse("2026-11-15T10:45:00Z")
        val to=Instant.parse("2026-11-15T11:15:00Z")
        val slots=listOf(StatisticsSlotRecord("booked","c","BOOKED",startAt=from,endAt=to,bookingStatus="CONFIRMED"),
            StatisticsSlotRecord("blocked","c","BLOCKED",startAt=from,endAt=to,bookingStatus="CONFIRMED"))
        assertEquals(listOf(OwnerPeakHour(17,15),OwnerPeakHour(18,15)),OwnerStatisticsCalculator.calculatePeakHours(slots))
    }
}

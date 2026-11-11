package com.example.booking_app.feature.owner.domain.statistics

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerRevenueFormulaTest {
    @Test
    fun separatesDepositedPaidAndRefundedCorrectly() {
        val records = listOf(
            StatisticsBookingRecord("b1", status = "CONFIRMED", paymentStatus = "DEPOSITED", depositAmountVnd = 100_000L),
            StatisticsBookingRecord("b2", status = "CONFIRMED", paymentStatus = "PAID", totalVnd = 300_000L),
            StatisticsBookingRecord("b3", status = "CONFIRMED", paymentStatus = "REFUNDED", refundAmountVnd = 150_000L),
            StatisticsBookingRecord("b4", status = "CONFIRMED", paymentStatus = "REFUNDED", refundAmountVnd = 50_000L),
        )

        val breakdown = OwnerStatisticsCalculator.calculateRevenue(records)

        assertEquals(100_000L, breakdown.depositedVnd)
        assertEquals(300_000L, breakdown.paidVnd)
        assertEquals(2, breakdown.refundedBookingCount)
        assertEquals(200_000L, breakdown.refundedVnd)
    }

    @Test
    fun atVenuePaymentOnlyCountedWhenPaid() {
        val records = listOf(
            StatisticsBookingRecord("b1", status = "CONFIRMED", paymentStatus = "PAID", paymentMethod = "AT_VENUE", totalVnd = 200_000L),
            StatisticsBookingRecord("b2", status = "CONFIRMED", paymentStatus = "UNPAID", paymentMethod = "AT_VENUE", totalVnd = 200_000L),
            StatisticsBookingRecord("b3", status = "PENDING", paymentStatus = null, paymentMethod = "AT_VENUE", totalVnd = 200_000L),
        )

        val breakdown = OwnerStatisticsCalculator.calculateRevenue(records)

        assertEquals(200_000L, breakdown.paidVnd)
        assertEquals(0L, breakdown.depositedVnd)
    }

    @Test
    fun excludesRejectedCancelledAndExpiredBookings() {
        val records = listOf(
            StatisticsBookingRecord("b1", status = "REJECTED", paymentStatus = "PAID", totalVnd = 100_000L),
            StatisticsBookingRecord("b2", status = "CANCELLED", paymentStatus = "PAID", totalVnd = 200_000L),
            StatisticsBookingRecord("b3", status = "EXPIRED", paymentStatus = "DEPOSITED", depositAmountVnd = 50_000L),
            StatisticsBookingRecord("b4", status = "CONFIRMED", paymentStatus = "PAID", totalVnd = 500_000L),
        )

        val breakdown = OwnerStatisticsCalculator.calculateRevenue(records)

        assertEquals(500_000L, breakdown.paidVnd)
        assertEquals(0L, breakdown.depositedVnd)
        assertEquals(0, breakdown.refundedBookingCount)
    }
}

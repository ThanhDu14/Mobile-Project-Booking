package com.example.booking_app.feature.owner.domain.statistics

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class StatisticsBookingRecord(
    val bookingId: String,
    val status: String,
    val paymentStatus: String?,
    val paymentMethod: String? = null,
    val totalVnd: Long = 0L,
    val depositAmountVnd: Long = 0L,
    val refundAmountVnd: Long = 0L,
)

data class StatisticsSlotRecord(
    val slotId: String,
    val courtId: String,
    val status: String,
    val blockReason: String? = null,
    val startAt: Instant,
    val endAt: Instant,
    val bookingStatus: String? = null,
)

object OwnerStatisticsCalculator {
    private val DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")

    /**
     * FR-016: Calculate revenue breakdown separating DEPOSITED, PAID, REFUNDED.
     * AT_VENUE is only counted when paymentStatus == "PAID".
     * Excludes REJECTED, CANCELLED, EXPIRED, and unpaid bookings.
     */
    fun calculateRevenue(bookings: List<StatisticsBookingRecord>): OwnerRevenueBreakdown {
        var depositedVnd = 0L
        var paidVnd = 0L
        var refundedBookingCount = 0
        var refundedVnd = 0L

        bookings.forEach { booking ->
            // Refunded money is a separate group, including rejected/cancelled bookings.
            if (booking.paymentStatus == "REFUNDED") {
                refundedBookingCount++
                refundedVnd += booking.refundAmountVnd
                return@forEach
            }
            if (booking.status in setOf("REJECTED", "CANCELLED", "EXPIRED")) {
                return@forEach
            }

            when (booking.paymentStatus) {
                "DEPOSITED" -> {
                    if (booking.paymentMethod != "AT_VENUE") depositedVnd += booking.depositAmountVnd
                }
                "PAID" -> {
                    paidVnd += booking.totalVnd
                }
                else -> {
                    // Unpaid or unknown status excluded from collected revenue
                }
            }
        }

        return OwnerRevenueBreakdown(
            depositedVnd = depositedVnd,
            paidVnd = paidVnd,
            refundedBookingCount = refundedBookingCount,
            refundedVnd = refundedVnd,
        )
    }

    /**
     * FR-017: Occupancy percentage using available slots as denominator (excluding BLOCKED),
     * and occupied slots (CONFIRMED, COMPLETED, NO_SHOW) as numerator. Deduplicates by slotId.
     */
    fun calculateOccupancy(slots: List<StatisticsSlotRecord>): Double {
        val distinctSlots = slots.distinctBy { it.slotId }
        val availableSlots = distinctSlots.filter { it.status != "BLOCKED" }
        if (availableSlots.isEmpty()) return 0.0

        val occupiedSlots = availableSlots.filter { isSlotOccupied(it) }
        return (occupiedSlots.size.toDouble() / availableSlots.size.toDouble()) * 100.0
    }

    /**
     * FR-018: Peak hours ranked by total occupied slot duration in 60-minute buckets,
     * calculated in local Asia/Ho_Chi_Minh timezone.
     */
    fun calculatePeakHours(
        slots: List<StatisticsSlotRecord>,
        zoneId: ZoneId = DEFAULT_ZONE,
    ): List<OwnerPeakHour> {
        val distinctSlots = slots.distinctBy { it.slotId }
        val occupied = distinctSlots.filter { it.status != "BLOCKED" && isSlotOccupied(it) }
        val hourMinutes = mutableMapOf<Int, Int>()

        occupied.forEach { slot ->
            var cursor = slot.startAt
            while (cursor < slot.endAt) {
                val local = cursor.atZone(zoneId)
                val nextHour = local.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant()
                val end = minOf(nextHour, slot.endAt)
                val minutes = Duration.between(cursor, end).toMinutes().toInt()
                hourMinutes[local.hour] = (hourMinutes[local.hour] ?: 0) + minutes
                cursor = end
            }
        }

        return hourMinutes.map { (hour, minutes) ->
            OwnerPeakHour(localHour = hour, occupiedDurationMinutes = minutes)
        }.sortedWith(compareByDescending<OwnerPeakHour> { it.occupiedDurationMinutes }.thenBy { it.localHour })
    }

    private fun isSlotOccupied(slot: StatisticsSlotRecord): Boolean =
        slot.bookingStatus in setOf("CONFIRMED", "COMPLETED", "NO_SHOW") ||
            (slot.bookingStatus == null && slot.status in setOf("CONFIRMED", "COMPLETED", "NO_SHOW", "BOOKED"))
}

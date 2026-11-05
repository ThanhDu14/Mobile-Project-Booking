package com.example.booking_app.feature.owner.domain.booking

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerBookingQueueTest {
    private val queue = OwnerBookingQueue()
    private val now = Instant.parse("2026-11-05T10:00:00Z")

    @Test
    fun pendingOrdersByNearestApprovalDeadlineAndLeavesMissingDeadlineLast() {
        val later = booking("later", OwnerBookingStatus.PENDING, deadline = "2026-11-05T11:00:00Z")
        val earlier = booking("earlier", OwnerBookingStatus.PENDING, deadline = "2026-11-05T10:30:00Z")
        val noDeadline = booking("unknown", OwnerBookingStatus.PENDING)

        assertEquals(
            listOf("earlier", "later", "unknown"),
            queue.filter(listOf(later, noDeadline, earlier), OwnerBookingFilter.PENDING, now).map { it.bookingId },
        )
    }

    @Test
    fun upcomingContainsOnlyActiveConfirmedBookingsAndPastContainsTerminalOrEndedBookings() {
        val upcoming = booking("future", OwnerBookingStatus.CONFIRMED, start = "2026-11-06T10:00:00Z")
        val ended = booking("ended", OwnerBookingStatus.CONFIRMED, start = "2026-11-04T10:00:00Z")
        val cancelled = booking("cancelled", OwnerBookingStatus.CANCELLED, start = "2026-11-06T10:00:00Z")
        val pending = booking("pending", OwnerBookingStatus.PENDING, start = "2026-11-06T10:00:00Z")

        assertEquals(listOf("future"), queue.filter(listOf(ended, pending, cancelled, upcoming), OwnerBookingFilter.UPCOMING, now).map { it.bookingId })
        assertEquals(
            listOf("cancelled", "ended"),
            queue.filter(listOf(ended, pending, cancelled, upcoming), OwnerBookingFilter.PAST, now).map { it.bookingId },
        )
    }

    @Test
    fun approvalCountdownIsClampedAtZeroAndAbsentWhenNoDeadline() {
        val pending = booking("pending", OwnerBookingStatus.PENDING, deadline = "2026-11-05T09:59:00Z")
        assertEquals(0L, pending.approvalTimeRemaining(now)?.seconds)
        assertNull(booking("confirmed", OwnerBookingStatus.CONFIRMED).approvalTimeRemaining(now))
        assertNotNull(booking("future", OwnerBookingStatus.PENDING, deadline = "2026-11-05T10:01:00Z").approvalTimeRemaining(now))
        assertTrue(booking("future", OwnerBookingStatus.PENDING, deadline = "2026-11-05T10:01:00Z").approvalTimeRemaining(now)!!.seconds > 0)
    }

    private fun booking(
        id: String,
        status: OwnerBookingStatus,
        start: String = "2026-11-06T10:00:00Z",
        deadline: String? = null,
    ) = OwnerBooking(
        bookingId = id,
        venueId = "venue-1",
        status = status,
        startAt = Instant.parse(start),
        endAt = Instant.parse(start).plusSeconds(3600),
        approvalDeadline = deadline?.let(Instant::parse),
    )
}

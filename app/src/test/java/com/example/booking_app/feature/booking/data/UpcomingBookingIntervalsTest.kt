package com.example.booking_app.feature.booking.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.Date

class UpcomingBookingIntervalsTest {
    private fun stamp(value: String) = Timestamp(Date.from(Instant.parse(value)))

    @Test
    fun `uses individual items rather than filling gaps between them`() {
        val items = listOf(
            mapOf("startAt" to stamp("2026-11-11T01:00:00Z"), "endAt" to stamp("2026-11-11T02:00:00Z")),
            mapOf("startAt" to stamp("2026-11-11T04:00:00Z"), "endAt" to stamp("2026-11-11T05:00:00Z")),
        )
        val intervals = bookingIntervals(mapOf("items" to items))
        assertEquals(2, intervals.size)
        assertEquals(Instant.parse("2026-11-11T04:00:00Z"), intervals[1].first)
    }

    @Test
    fun `legacy recurring date and time use Vietnam timezone`() {
        val intervals = bookingIntervals(mapOf("date" to "20261111", "startTime" to "18:00", "endTime" to "19:00"))
        assertEquals(Instant.parse("2026-11-11T11:00:00Z"), intervals.single().first)
        assertEquals(Instant.parse("2026-11-11T12:00:00Z"), intervals.single().second)
    }

    @Test
    fun `invalid data does not silently return no conflicts`() {
        assertThrows(IllegalStateException::class.java) { bookingIntervals(emptyMap()) }
    }
}

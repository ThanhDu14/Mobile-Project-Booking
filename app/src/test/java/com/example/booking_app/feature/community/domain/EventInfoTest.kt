package com.example.booking_app.feature.community.domain

import com.example.booking_app.feature.community.domain.model.EventInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EventInfoTest {

    private val at = Instant.parse("2026-11-20T12:00:00Z")

    private fun event(attendees: Int = 0, expected: Int = 10) =
        EventInfo("Giao luu", at, "San A", expectedCount = expected, attendeeCount = attendees, iAmAttending = false)

    @Test
    fun `isPast dung moc at le now`() {
        assertFalse(event().isPast(at.minusSeconds(1)))
        assertTrue(event().isPast(at))
        assertTrue(event().isPast(at.plusSeconds(1)))
    }

    @Test
    fun `isOverExpected chi khi vuot, bang thi chua`() {
        assertFalse(event(attendees = 9).isOverExpected)
        assertFalse(event(attendees = 10).isOverExpected)
        assertTrue(event(attendees = 11).isOverExpected)
    }
}

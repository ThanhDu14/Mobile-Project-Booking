package com.example.booking_app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VenueRatingSummaryTest {

    @Test
    fun `tinh diem tung tieu chi, lam tron mot chu so half-up`() {
        val s = VenueRatingSummary.fromVenue(
            ratingAvg = 4.6,
            ratingCount = 128,
            ratingSums = mapOf("court" to 602L, "cleanliness" to 563L, "service" to 589L, "overall10" to 1178L),
        )
        assertEquals(4.6, s.average, 0.0)
        assertEquals(128, s.count)
        assertEquals(4.7, s.courtAvg!!, 0.0)
        assertEquals(4.4, s.cleanlinessAvg!!, 0.0)
        assertEquals(4.6, s.serviceAvg!!, 0.0)
    }

    @Test
    fun `half-up o moc 05`() {
        // 9 / 2 = 4.5 → 4.5; 9.1 không có; 45 / 10 = 4.5; 4.25 → 4.3
        val s = VenueRatingSummary.fromVenue(4.0, 4, mapOf("court" to 17L, "cleanliness" to 18L, "service" to 19L))
        assertEquals(4.3, s.courtAvg!!, 0.0) // 4.25
        assertEquals(4.5, s.cleanlinessAvg!!, 0.0)
        assertEquals(4.8, s.serviceAvg!!, 0.0) // 4.75
    }

    @Test
    fun `chua co danh gia thi cac tieu chi null`() {
        val s = VenueRatingSummary.fromVenue(0.0, 0, mapOf("court" to 0L))
        assertEquals(0, s.count)
        assertNull(s.courtAvg)
        assertNull(s.cleanlinessAvg)
        assertNull(s.serviceAvg)
    }

    @Test
    fun `thieu ratingSums hoac mot khoa thi tieu chi do null`() {
        val noSums = VenueRatingSummary.fromVenue(4.2, 5, null)
        assertEquals(4.2, noSums.average, 0.0)
        assertNull(noSums.courtAvg)

        val partial = VenueRatingSummary.fromVenue(4.2, 5, mapOf("court" to 21L))
        assertEquals(4.2, partial.courtAvg!!, 0.0)
        assertNull(partial.cleanlinessAvg)
        assertNull(partial.serviceAvg)
    }

    @Test
    fun `gia tri null thi la 0`() {
        val s = VenueRatingSummary.fromVenue(null, null, null)
        assertEquals(0.0, s.average, 0.0)
        assertEquals(0, s.count)
    }
}

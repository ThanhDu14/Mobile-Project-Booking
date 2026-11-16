package com.example.booking_app.feature.search.domain

import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class CourtSearchAvailabilityTest {
    private val now = ZonedDateTime.of(2026, 11, 12, 10, 0, 0, 0, BookingWindow.ZONE)
    private val timestamp = now.toInstant().toEpochMilli()
    private val window = requireNotNull(BookingSearchWindow.parse("20261113", "1900", "2000"))

    @Test
    fun heldSlotsAreBusyUntilExpiryAndMissingExpiryFailsClosed() {
        val hold = OccupiedSlot("a", TimeOfDay(19 * 60), "HELD", timestamp + 1)
        assertFalse(hasContinuousCourt(setOf("a"), window, listOf(hold), timestamp))
        assertFalse(hasContinuousCourt(setOf("a"), window, listOf(hold.copy(expiresAt = null)), timestamp))
        assertTrue(hasContinuousCourt(setOf("a"), window, listOf(hold), timestamp + 1))
    }

    @Test
    fun distinctCourtsCannotCoverDifferentHalves() {
        val first = OccupiedSlot("a", TimeOfDay(19 * 60), "BOOKED", null)
        val second = OccupiedSlot("b", TimeOfDay(19 * 60 + 30), "BLOCKED", null)
        assertFalse(hasContinuousCourt(setOf("a", "b"), window, listOf(first, second), timestamp))
        assertTrue(hasContinuousCourt(setOf("a", "b", "c"), window, listOf(first, second), timestamp))
    }

    @Test
    fun bookingWindowRequiresTwoFutureAlignedSlotsAndAcceptsTheLastDay() {
        fun allowed(day: String, start: String, end: String) =
            requireNotNull(BookingSearchWindow.parse(day, start, end)).isBookableAt(now)

        assertFalse(allowed("20261113", "1900", "1930"))
        assertFalse(allowed("20261112", "1000", "1100"))
        assertFalse(allowed("20261213", "2300", "2400"))
        assertTrue(allowed("20261212", "2300", "2400"))
    }

    @Test
    fun vietnameseNameAndPriceUseStructuredFilters() {
        assertTrue(matchesVenueName("hoa bi", "Sân cầu lông Hòa Bình"))
        assertFalse(matchesVenueName("hoa b", "Sân cầu lông Hòa An"))
        val criteria = CourtSearchCriteria(priceMax = 100_000)
        assertTrue(criteria.acceptsPrice(80_000))
        assertFalse(criteria.acceptsPrice(150_000))
        assertFalse(criteria.acceptsPrice(null))
    }
}

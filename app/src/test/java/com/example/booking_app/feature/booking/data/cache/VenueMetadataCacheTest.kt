package com.example.booking_app.feature.booking.data.cache

import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.VenueInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VenueMetadataCacheTest {

    private class FakeAppClock(var currentTime: Long = 1_000_000L) : AppClock {
        override fun now(): Long = currentTime
        override fun elapsedRealtime(): Long = currentTime
    }

    private lateinit var clock: FakeAppClock
    private lateinit var cache: VenueMetadataCache

    private val venue1 = VenueInfo(
        id = "venue-1",
        name = "Sân Cầu Lông A",
        address = "123 Đường A",
        districtName = "Quận 1",
        openTime = "0600",
        closeTime = "2200",
        status = "ACTIVE",
        acceptsRecurring = true
    )

    private val courts1 = listOf(
        Court(id = "c1", name = "Sân 1", surfaceType = "Thảm", isActive = true, sortOrder = 1)
    )

    private val priceRules1 = listOf(
        PriceRule(id = "pr1", daysOfWeek = listOf(1, 2, 3), startTime = "0600", endTime = "2200", pricePerHour = 100_000L, label = "NORMAL")
    )

    @Before
    fun setUp() {
        clock = FakeAppClock(1_000_000L)
        cache = VenueMetadataCache(clock)
    }

    @Test
    fun getVenueInfo_whenCachedAndWithinTTL_returnsCachedData() {
        cache.putVenueInfo("venue-1", venue1)

        // Sau 4 phút (< 5 phút TTL)
        clock.currentTime += 4 * 60 * 1000L

        val result = cache.getVenueInfo("venue-1")
        assertNotNull(result)
        assertEquals("Sân Cầu Lông A", result?.name)
    }

    @Test
    fun getVenueInfo_whenExpiredAfter5Minutes_returnsNull() {
        cache.putVenueInfo("venue-1", venue1)

        // Sau 5 phút 1 mili-giây
        clock.currentTime += 5 * 60 * 1000L + 1L

        val result = cache.getVenueInfo("venue-1")
        assertNull(result)
    }

    @Test
    fun getVenueInfo_whenForceRefreshTrue_returnsNullEvenIfValid() {
        cache.putVenueInfo("venue-1", venue1)

        val result = cache.getVenueInfo("venue-1", forceRefresh = true)
        assertNull(result)
    }

    @Test
    fun courtsAndPriceRulesCache_withinAndBeyondTTL() {
        cache.putCourts("venue-1", courts1)
        cache.putPriceRules("venue-1", priceRules1)

        // 2 phút sau: vẫn còn hạn
        clock.currentTime += 2 * 60 * 1000L
        assertEquals(1, cache.getCourts("venue-1")?.size)
        assertEquals(1, cache.getPriceRules("venue-1")?.size)

        // 6 phút sau (> 5 phút TTL): hết hạn
        clock.currentTime += 4 * 60 * 1000L
        assertNull(cache.getCourts("venue-1"))
        assertNull(cache.getPriceRules("venue-1"))
    }

    @Test
    fun clear_removesSpecificVenueOrAll() {
        cache.putVenueInfo("venue-1", venue1)
        cache.clear("venue-1")
        assertNull(cache.getVenueInfo("venue-1"))

        cache.putVenueInfo("venue-1", venue1)
        cache.clear()
        assertNull(cache.getVenueInfo("venue-1"))
    }
}

package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.util.AppClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VenueStatusCacheTest {

    private class FakeClock(var nowMs: Long = 1_000_000L) : AppClock {
        override fun now(): Long = nowMs
        override fun elapsedRealtime(): Long = nowMs
    }

    private class CountingSource(val states: MutableMap<String, Boolean?>) : VenueStatusSource {
        val calls = mutableListOf<String>()
        override suspend fun isActive(venueId: String): Boolean? {
            calls += venueId
            return states[venueId]
        }
    }

    private val clock = FakeClock()
    private val source = CountingSource(mutableMapOf("a" to true, "h" to false, "x" to null))
    private val cache = VenueStatusCache(source, clock)

    @Test
    fun `doc theo lo, cache 5 phut`() = runTest {
        assertEquals(mapOf("a" to true, "h" to false), cache.activeStates(listOf("a", "h", "a")))
        assertEquals(2, source.calls.size)

        clock.nowMs += VenueStatusCache.TTL_MS - 1
        cache.activeStates(listOf("a", "h"))
        assertEquals(2, source.calls.size)

        clock.nowMs += 1
        source.states["h"] = true
        assertEquals(mapOf("a" to true, "h" to true), cache.activeStates(listOf("a", "h")))
        assertEquals(4, source.calls.size)
    }

    @Test
    fun `khong biet trang thai - coi la hoat dong va khong cache`() = runTest {
        assertEquals(true, cache.isActive("x"))
        assertEquals(true, cache.isActive("x"))
        assertEquals(listOf("x", "x"), source.calls)
    }

    @Test
    fun `danh sach rong - khong doc`() = runTest {
        assertEquals(emptyMap<String, Boolean>(), cache.activeStates(emptyList()))
        assertEquals(0, source.calls.size)
    }
}

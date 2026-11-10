package com.example.booking_app.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceFormatterTest {

    @Test
    fun `duoi 1000 m hien met, lam tron 10 m`() {
        assertEquals("850 m", DistanceFormatter.format(849.0))
        assertEquals("10 m", DistanceFormatter.format(12.0))
        assertEquals("0 m", DistanceFormatter.format(3.0))
    }

    @Test
    fun `tu 1000 m hien km mot chu so thap phan dau phay`() {
        assertEquals("1,0 km", DistanceFormatter.format(996.0))
        assertEquals("1,2 km", DistanceFormatter.format(1234.0))
        assertEquals("12,5 km", DistanceFormatter.format(12_456.0))
    }
}

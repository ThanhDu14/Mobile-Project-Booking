package com.example.booking_app.core.pricing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class DayConventionTest {

    @Test
    fun `ISO 1 la thu hai, 7 la chu nhat`() {
        assertEquals(DayConvention.ISO, DayConvention.CURRENT)
        assertEquals(DayOfWeek.MONDAY, DayConvention.toDayOfWeek(1))
        assertEquals(DayOfWeek.SATURDAY, DayConvention.toDayOfWeek(6))
        assertEquals(DayOfWeek.SUNDAY, DayConvention.toDayOfWeek(7))
        DayOfWeek.entries.forEach { assertEquals(it, DayConvention.toDayOfWeek(DayConvention.fromDayOfWeek(it))) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `0 bi tu choi`() {
        DayConvention.toDayOfWeek(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `8 bi tu choi`() {
        DayConvention.toDayOfWeek(8)
    }
}

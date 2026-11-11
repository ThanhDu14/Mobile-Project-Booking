package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.FilterChip
import com.example.booking_app.feature.dropin.domain.model.FilterError
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DropInFilterTest {

    private val date = LocalDate.of(2026, 11, 12)

    private val full = DropInFilter(
        districtIds = linkedSetOf("q1", "q3"),
        date = date,
        timeSlot = TimeSlot.EVENING,
        level = Level.INTERMEDIATE,
        maxPrice = 80_000,
    )

    @Test
    fun `bo loc rong - khong co chip va hop le`() {
        val empty = DropInFilter()
        assertTrue(empty.isEmpty)
        assertEquals(emptyList<FilterChip>(), empty.chips())
        assertNull(empty.validate())
    }

    @Test
    fun `chip theo thu tu quan, ngay, khung gio, trinh do, gia`() {
        assertEquals(
            listOf(
                FilterChip.District("q1"),
                FilterChip.District("q3"),
                FilterChip.Date(date),
                FilterChip.Slot(TimeSlot.EVENING),
                FilterChip.Lvl(Level.INTERMEDIATE),
                FilterChip.MaxPrice(80_000),
            ),
            full.chips(),
        )
    }

    @Test
    fun `bo tung chip - chi mat dung truong do`() {
        assertEquals(full.copy(districtIds = setOf("q3")), full.without(FilterChip.District("q1")))
        assertEquals(full.copy(date = null), full.without(FilterChip.Date(date)))
        assertEquals(full.copy(timeSlot = null), full.without(FilterChip.Slot(TimeSlot.EVENING)))
        assertEquals(full.copy(level = null), full.without(FilterChip.Lvl(Level.INTERMEDIATE)))
        assertEquals(full.copy(maxPrice = null), full.without(FilterChip.MaxPrice(80_000)))
    }

    @Test
    fun `bo het chip - thanh bo loc rong`() {
        val cleared = full.chips().fold(full) { acc, chip -> acc.without(chip) }
        assertTrue(cleared.isEmpty)
    }

    @Test
    fun `bo chip khong khop gia tri hien tai - giu nguyen`() {
        assertEquals(full, full.without(FilterChip.Slot(TimeSlot.MORNING)))
        assertEquals(full, full.without(FilterChip.District("q9")))
    }

    @Test
    fun `15 quan hop le, 16 quan bao loi`() {
        val fifteen = DropInFilter(districtIds = (1..15).map { "d$it" }.toSet())
        assertNull(fifteen.validate())
        val sixteen = DropInFilter(districtIds = (1..16).map { "d$it" }.toSet())
        assertEquals(FilterError.TooManyDistricts(DropInFilter.MAX_DISTRICTS), sixteen.validate())
    }

    @Test
    fun `khung gio theo bien 05h 11h 13h 18h 23h`() {
        assertNull(TimeSlot.of(LocalTime.of(4, 59)))
        assertEquals(TimeSlot.MORNING, TimeSlot.of(LocalTime.of(5, 0)))
        assertEquals(TimeSlot.MORNING, TimeSlot.of(LocalTime.of(10, 59)))
        assertEquals(TimeSlot.NOON, TimeSlot.of(LocalTime.of(11, 0)))
        assertEquals(TimeSlot.NOON, TimeSlot.of(LocalTime.of(12, 30)))
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.of(LocalTime.of(13, 0)))
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.of(LocalTime.of(17, 30)))
        assertEquals(TimeSlot.EVENING, TimeSlot.of(LocalTime.of(18, 0)))
        assertEquals(TimeSlot.EVENING, TimeSlot.of(LocalTime.of(22, 30)))
        assertNull(TimeSlot.of(LocalTime.of(23, 0)))
    }

    @Test
    fun `khung gio tu Instant tinh theo gio Asia Ho_Chi_Minh`() {
        // 11:00Z = 18:00 giờ Việt Nam
        assertEquals(TimeSlot.EVENING, TimeSlot.of(Instant.parse("2026-11-12T11:00:00Z")))
        // 03:59Z = 10:59 giờ Việt Nam
        assertEquals(TimeSlot.MORNING, TimeSlot.of(Instant.parse("2026-11-12T03:59:00Z")))
    }
}

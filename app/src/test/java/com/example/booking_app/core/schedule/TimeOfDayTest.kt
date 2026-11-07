package com.example.booking_app.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TimeOfDayTest {

    @Test
    fun `HHmm doi sang phut va nguoc lai`() {
        assertEquals(0, TimeOfDay.parse("0000")!!.minutes)
        assertEquals(19 * 60 + 30, TimeOfDay.parse("1930")!!.minutes)
        assertEquals("19:30", TimeOfDay.parse("1930")!!.format())
        assertEquals("1930", TimeOfDay.parse("1930")!!.toHhmm())
    }

    @Test
    fun `dinh dang sai tra null`() {
        listOf("", "930", "19:30", "2460", "2500", "abcd", "19300").forEach {
            assertNull("'$it' phải sai định dạng", TimeOfDay.parse(it))
        }
    }

    @Test
    fun `2400 chi hop le o vi tri gio ket thuc`() {
        assertNull(TimeOfDay.parse("2400"))
        val end = TimeOfDay.parseEnd("2400")!!
        assertEquals(1440, end.minutes)
        assertEquals("24:00", end.format())
        assertEquals("2400", end.toHhmm())
        assertNull(TimeOfDay.parseEnd("2430"))
        assertEquals(TimeOfDay.parse("2300"), TimeOfDay.parseEnd("2300"))
    }

    @Test
    fun `khung 2300 den 2400 liet ke dung 2 khung 30 phut`() {
        val slots = SlotMath.slots(TimeOfDay.parse("2300")!!, TimeOfDay.parseEnd("2400")!!)
        assertEquals(listOf("2300", "2330"), slots.map { it.toHhmm() })
    }

    @Test
    fun `chi nhan moc 00 va 30`() {
        assertTrue(SlotMath.isAligned(TimeOfDay.parse("1930")!!))
        assertFalse(SlotMath.isAligned(TimeOfDay.parse("1915")!!))
    }

    @Test
    fun `SlotId tat dinh`() {
        assertEquals("c1_20261020_1930", SlotId.of("c1", LocalDate.of(2026, 10, 20), TimeOfDay.parse("1930")!!))
    }

    @Test
    fun `BookingWindow theo gio Viet Nam va 30 ngay`() {
        assertEquals("Asia/Ho_Chi_Minh", BookingWindow.ZONE.id)
        assertEquals(30, BookingWindow.ADVANCE_DAYS)
        val today = LocalDate.of(2026, 10, 27)
        assertTrue(BookingWindow.isDateAllowed(today, today))
        assertTrue(BookingWindow.isDateAllowed(today.plusDays(30), today))
        assertFalse(BookingWindow.isDateAllowed(today.plusDays(31), today))
        assertFalse(BookingWindow.isDateAllowed(today.minusDays(1), today))
    }

    @Test
    fun `atDate cua 2400 la 0 gio ngay hom sau`() {
        val date = LocalDate.of(2026, 10, 27)
        assertEquals(LocalDateTime.of(2026, 10, 28, 0, 0), TimeOfDay.parseEnd("2400")!!.atDate(date))
    }
}

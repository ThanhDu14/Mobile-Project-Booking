package com.example.booking_app.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FormatTest {

    @Test
    fun `tien VND - dau cham hang nghin va hau to d`() {
        assertEquals("80.000 đ", MoneyFormat.vnd(80_000))
        assertEquals("0 đ", MoneyFormat.vnd(0))
        assertEquals("500 đ", MoneyFormat.vnd(500))
        assertEquals("1.200.000 đ", MoneyFormat.vnd(1_200_000))
        assertEquals("-60.000 đ", MoneyFormat.vnd(-60_000))
    }

    @Test
    fun `mui gio la Asia Ho_Chi_Minh`() {
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), TimeFormat.ZONE)
    }

    @Test
    fun `gio va khoang gio theo gio Viet Nam`() {
        val start = Instant.parse("2026-11-12T11:00:00Z") // 18:00 +07
        val end = Instant.parse("2026-11-12T13:30:00Z") // 20:30 +07
        assertEquals("18:00", TimeFormat.time(start))
        assertEquals("18:00–20:30", TimeFormat.timeRange(start, end))
    }

    @Test
    fun `ngay theo gio Viet Nam - qua nua dem UTC van dung ngay`() {
        val lateUtc = Instant.parse("2026-11-11T18:30:00Z") // 01:30 ngày 12/11 +07
        assertEquals("12/11/2026", TimeFormat.date(lateUtc))
        assertEquals(LocalDate.of(2026, 11, 12), TimeFormat.localDate(lateUtc))
    }

    @Test
    fun `thu va ngay - T2 toi CN`() {
        assertEquals("T4, 11/11", TimeFormat.dayOfWeekDate(Instant.parse("2026-11-11T03:00:00Z")))
        assertEquals("T5, 12/11", TimeFormat.dayOfWeekDate(Instant.parse("2026-11-12T03:00:00Z")))
        assertEquals("CN, 15/11", TimeFormat.dayOfWeekDate(Instant.parse("2026-11-15T03:00:00Z")))
        assertEquals("T2, 16/11", TimeFormat.dayOfWeekDate(Instant.parse("2026-11-16T03:00:00Z")))
    }
}

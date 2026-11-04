package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SearchWindowArgsTest {

    // Thứ hai 26/10/2026 20:00 giờ Việt Nam.
    private val now: ZonedDateTime = LocalDateTime.of(2026, 10, 26, 20, 0).atZone(BookingWindow.ZONE)

    @Test
    fun `du 3 tham so hop le`() {
        assertEquals(
            SearchWindow(LocalDate.of(2026, 10, 27), TimeOfDay.parse("1900")!!, TimeOfDay.parse("2100")!!),
            SearchWindowArgs.parse("20261027", "1900", "2100", now),
        )
    }

    @Test
    fun `thieu mot tham so thi null`() {
        assertNull(SearchWindowArgs.parse(null, "1900", "2100", now))
        assertNull(SearchWindowArgs.parse("20261027", null, "2100", now))
        assertNull(SearchWindowArgs.parse("20261027", "1900", null, now))
    }

    @Test
    fun `sai dinh dang thi null`() {
        assertNull(SearchWindowArgs.parse("2026-10-27", "1900", "2100", now))
        assertNull(SearchWindowArgs.parse("20261332", "1900", "2100", now))
        assertNull(SearchWindowArgs.parse("20261027", "19:00", "2100", now))
        assertNull(SearchWindowArgs.parse("20261027", "1900", "2500", now))
    }

    @Test
    fun `start lon hon hoac bang end thi null`() {
        assertNull(SearchWindowArgs.parse("20261027", "2100", "1900", now))
        assertNull(SearchWindowArgs.parse("20261027", "1900", "1900", now))
    }

    @Test
    fun `khong phai moc 00 hoac 30 thi null`() {
        assertNull(SearchWindowArgs.parse("20261027", "1915", "2100", now))
        assertNull(SearchWindowArgs.parse("20261027", "1900", "2045", now))
    }

    @Test
    fun `end 2400 hop le`() {
        assertEquals(TimeOfDay.END, SearchWindowArgs.parse("20261027", "2200", "2400", now)!!.end)
    }

    @Test
    fun `khung da qua thi null, tinh theo gio Viet Nam`() {
        assertNull(SearchWindowArgs.parse("20261026", "1900", "2100", now)) // 19:00 < 20:00
        assertNull(SearchWindowArgs.parse("20261026", "2000", "2100", now)) // đúng bằng now
        assertEquals(TimeOfDay.parse("2030"), SearchWindowArgs.parse("20261026", "2030", "2100", now)!!.start)
        // Cùng thời điểm nhưng đưa vào dạng UTC (13:00Z) vẫn ra đúng.
        val utc = now.withZoneSameInstant(ZoneOffset.UTC)
        assertNull(SearchWindowArgs.parse("20261026", "1930", "2100", utc))
    }
}

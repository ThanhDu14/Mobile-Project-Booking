package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.courtdetail.domain.model.OpeningStatus
import com.example.booking_app.feature.courtdetail.vnMillis
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OpeningHoursTest {

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        LocalDateTime.of(2026, 10, 27, hour, minute).atZone(BookingWindow.ZONE)

    private fun t(hhmm: String) = TimeOfDay.parseEnd(hhmm)!!

    @Test
    fun `0500-2300 luc 20h dang mo, 23h30 da dong mo lai 05h`() {
        assertEquals(OpeningStatus.Open(t("2300")), OpeningHours.status("0500", "2300", at(20)))
        assertEquals(OpeningStatus.Closed(t("0500")), OpeningHours.status("0500", "2300", at(23, 30)))
        assertEquals(OpeningStatus.Closed(t("0500")), OpeningHours.status("0500", "2300", at(4, 59)))
        assertEquals(OpeningStatus.Open(t("2300")), OpeningHours.status("0500", "2300", at(5, 0)))
    }

    @Test
    fun `qua nua dem 0500-0100`() {
        assertEquals(OpeningStatus.Open(t("0100")), OpeningHours.status("0500", "0100", at(0, 30)))
        assertEquals(OpeningStatus.Closed(t("0500")), OpeningHours.status("0500", "0100", at(2)))
        assertEquals(OpeningStatus.Open(t("0100")), OpeningHours.status("0500", "0100", at(22)))
    }

    @Test
    fun `0500-2400`() {
        assertEquals(OpeningStatus.Closed(t("0500")), OpeningHours.status("0500", "2400", at(0, 30)))
        assertEquals(OpeningStatus.Open(t("2400")), OpeningHours.status("0500", "2400", at(23, 59)))
    }

    @Test
    fun `mo ca ngay`() {
        assertEquals(OpeningStatus.AllDay, OpeningHours.status("0000", "2400", at(3)))
        assertEquals(OpeningStatus.AllDay, OpeningHours.status("0600", "0600", at(3)))
    }

    @Test
    fun `tinh theo gio Viet Nam ke ca khi dau vao la UTC`() {
        val utc = at(23, 30).withZoneSameInstant(ZoneOffset.UTC) // 16:30Z
        assertEquals(OpeningStatus.Closed(t("0500")), OpeningHours.status("0500", "2300", utc))
    }

    @Test
    fun `gio sai dinh dang thi null`() {
        assertNull(OpeningHours.status("", "2300", at(20)))
        assertNull(OpeningHours.status("2400", "2300", at(20)))
    }

    @Test
    fun `ticker phat moi phut theo AppClock`() = runTest {
        val base = vnMillis(2026, 10, 27, 22, 58) + 30_000 // 22:58:30
        val clock = FakeClock { base + testScheduler.currentTime }
        val ticks = OpeningTicker.ticks(clock).take(3).toList()
        assertEquals(listOf("22:58", "22:59", "23:00"), ticks.map { "%02d:%02d".format(it.hour, it.minute) })
    }
}

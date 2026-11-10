package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.SlotMath
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Đọc tham số giờ từ tìm kiếm (research R1): đủ cả ba, đúng định dạng (`yyyyMMdd`, `HHmm`), mốc 30 phút,
 * `start < end` và chưa qua giờ theo `Asia/Ho_Chi_Minh`. Không thỏa thì `null` (màn hình coi như không có khung).
 */
object SearchWindowArgs {

    fun parse(date: String?, start: String?, end: String?, now: ZonedDateTime): SearchWindow? {
        val day = parseDate(date) ?: return null
        val from = TimeOfDay.parse(start) ?: return null
        val to = TimeOfDay.parseEnd(end) ?: return null
        if (!SlotMath.isAligned(from) || !SlotMath.isAligned(to) || from >= to) return null
        val window = SearchWindow(day, from, to)
        return window.takeIf { isUpcoming(it, now) }
    }

    /** Khung còn hiệu lực khi giờ bắt đầu còn sau [now]. */
    fun isUpcoming(window: SearchWindow, now: ZonedDateTime): Boolean =
        window.start.atDate(window.date).isAfter(now.withZoneSameInstant(BookingWindow.ZONE).toLocalDateTime())

    private fun parseDate(raw: String?): LocalDate? {
        if (raw == null || raw.length != 8) return null
        return try {
            LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

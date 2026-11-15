package com.example.booking_app.core.schedule

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A dated search window passed through court detail to the slot grid. */
data class BookingSearchWindow(val date: LocalDate, val start: TimeOfDay, val end: TimeOfDay) {
    fun isBookableAt(now: ZonedDateTime): Boolean =
        BookingWindow.isDateAllowed(date, now.toLocalDate()) &&
            SlotMath.isAligned(start) && SlotMath.isAligned(end) &&
            end.minutes - start.minutes >= 2 * SlotMath.STEP_MINUTES &&
            start.atDate(date) > now.toLocalDateTime()

    fun dateKey(): String = date.format(DateTimeFormatter.BASIC_ISO_DATE)

    companion object {
        fun parse(date: String?, start: String?, end: String?): BookingSearchWindow? {
            val day = parseDate(date) ?: return null
            val from = TimeOfDay.parse(start) ?: return null
            val to = TimeOfDay.parseEnd(end) ?: return null
            return BookingSearchWindow(day, from, to)
        }

        fun parseDate(date: String?): LocalDate? {
            if (date == null || !date.matches(Regex("[0-9]{8}"))) return null
            return runCatching { LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
        }
    }
}

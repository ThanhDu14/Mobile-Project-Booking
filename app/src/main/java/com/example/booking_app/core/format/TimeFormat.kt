package com.example.booking_app.core.format

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ngày giờ hiển thị theo giờ `Asia/Ho_Chi_Minh`, không phụ thuộc múi giờ của máy (FR-027). */
object TimeFormat {

    val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /** `12/11/2026`. */
    fun date(instant: Instant): String = DATE.format(instant.atZone(ZONE))

    /** Ngày lịch theo giờ Việt Nam. */
    fun localDate(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()

    /** `18:00`. */
    fun time(instant: Instant): String = TIME.format(instant.atZone(ZONE))

    /** `18:00–20:30` (gạch nối en dash). */
    fun timeRange(start: Instant, end: Instant): String = "${time(start)}–${time(end)}"

    /** `T4, 12/11`; Chủ nhật là `CN`. */
    fun dayOfWeekDate(instant: Instant): String {
        val zoned = instant.atZone(ZONE)
        return "${dayOfWeekShort(zoned.dayOfWeek)}, ${DAY_MONTH.format(zoned)}"
    }

    /** `T2`…`T7`, `CN`. */
    fun dayOfWeekShort(day: DayOfWeek): String =
        if (day == DayOfWeek.SUNDAY) "CN" else "T${day.value + 1}"
}

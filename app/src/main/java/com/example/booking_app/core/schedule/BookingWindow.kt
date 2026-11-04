package com.example.booking_app.core.schedule

import java.time.LocalDate
import java.time.ZoneId

/** Giới hạn ngày đặt sân và múi giờ của cả app (spec 020 FR-006, 040 FR-001). */
object BookingWindow {
    const val ADVANCE_DAYS = 30

    val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    fun isDateAllowed(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(today) && !date.isAfter(today.plusDays(ADVANCE_DAYS.toLong()))
}

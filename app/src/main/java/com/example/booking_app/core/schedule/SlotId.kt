package com.example.booking_app.core.schedule

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** ID tất định của slot: `{courtId}_{yyyyMMdd}_{HHmm}` (đặc tả CSDL §5). */
object SlotId {
    fun of(courtId: String, date: LocalDate, start: TimeOfDay): String =
        "${courtId}_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}_${start.toHhmm()}"
}

package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.feature.courtdetail.domain.model.OpeningStatus
import java.time.ZonedDateTime

/** Trạng thái mở cửa theo giờ Việt Nam (research R6). Giờ sai định dạng → `null` (ẩn chip). */
object OpeningHours {

    fun status(openTime: String, closeTime: String, now: ZonedDateTime): OpeningStatus? {
        val open = TimeOfDay.parse(openTime) ?: return null
        val close = TimeOfDay.parseEnd(closeTime) ?: return null
        if (open == close || (open == TimeOfDay.MIDNIGHT && close == TimeOfDay.END)) return OpeningStatus.AllDay
        val local = now.withZoneSameInstant(BookingWindow.ZONE)
        val t = local.hour * 60 + local.minute
        val isOpen = if (open < close) {
            t >= open.minutes && t < close.minutes
        } else {
            t >= open.minutes || t < close.minutes // qua nửa đêm
        }
        return if (isOpen) OpeningStatus.Open(close) else OpeningStatus.Closed(open)
    }
}

package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.util.AppClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZonedDateTime

/** Phát giờ Việt Nam ngay khi thu thập rồi đầu mỗi phút, để trạng thái mở cửa và khung giờ tự cập nhật (R6). */
object OpeningTicker {
    private const val MINUTE_MS = 60_000L

    fun ticks(clock: AppClock): Flow<ZonedDateTime> = flow {
        while (true) {
            val now = clock.now()
            emit(Instant.ofEpochMilli(now).atZone(BookingWindow.ZONE))
            delay(MINUTE_MS - Math.floorMod(now, MINUTE_MS))
        }
    }
}

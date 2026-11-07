package com.example.booking_app.core.pricing

import java.time.DayOfWeek

/**
 * Chỗ duy nhất đổi số trong `daysOfWeek` sang [DayOfWeek] (spec 030 research R5).
 * Seed dùng ISO (1 = Thứ hai … 7 = Chủ nhật). Nhóm B + D chưa chốt: nếu chốt kiểu lịch (1 = Chủ nhật)
 * thì đổi [CURRENT] và file `pricing/window-price-vectors.json` cùng lúc.
 */
object DayConvention {
    const val ISO = "ISO"
    const val CALENDAR = "CALENDAR"

    const val CURRENT = ISO

    fun toDayOfWeek(value: Int): DayOfWeek {
        require(value in 1..7) { "daysOfWeek ngoài 1..7: $value" }
        return if (CURRENT == ISO) DayOfWeek.of(value) else DayOfWeek.of((value + 5) % 7 + 1)
    }

    fun fromDayOfWeek(day: DayOfWeek): Int =
        if (CURRENT == ISO) day.value else day.value % 7 + 1
}

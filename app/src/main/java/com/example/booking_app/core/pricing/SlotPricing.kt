package com.example.booking_app.core.pricing

import com.example.booking_app.core.schedule.SlotMath
import com.example.booking_app.core.schedule.TimeOfDay
import java.time.LocalDate

/**
 * Giá một khung giờ, dùng chung cho thẻ kết quả (020), chi tiết sân (030) và lưới giờ (040) để ba màn hình
 * ra cùng một số (SC-002). Ca kiểm thử: `app/src/test/resources/pricing/window-price-vectors.json`.
 */
object SlotPricing {

    /** Cộng giá từng khung 30 phút của `[start, end)`; có khung không thuộc dòng giá nào → `null` ("Liên hệ"). */
    fun priceForWindow(rules: List<PriceRule>, date: LocalDate, start: TimeOfDay, end: TimeOfDay): Long? {
        if (end <= start) return null
        val day = DayConvention.fromDayOfWeek(date.dayOfWeek)
        val dayRules = rules.filter { day in it.daysOfWeek }
        var total = 0L
        for (slot in SlotMath.slots(start, end)) {
            val slotEnd = slot.plusMinutes(SlotMath.STEP_MINUTES)
            val rule = dayRules.firstOrNull { r ->
                val rs = TimeOfDay.parse(r.startTime) ?: return@firstOrNull false
                val re = TimeOfDay.parseEnd(r.endTime) ?: return@firstOrNull false
                rs <= slot && slotEnd <= re
            } ?: return null
            total += rule.pricePerSlot
        }
        return total
    }

    /** Quy ra đ/giờ: `total × 60 / số phút`, làm tròn xuống tới đồng. */
    fun perHour(total: Long, start: TimeOfDay, end: TimeOfDay): Long {
        val minutes = end.minutes - start.minutes
        require(minutes > 0) { "Khung giờ rỗng" }
        return total * 60 / minutes
    }

    /** Các dòng giá của ngày [date] có giao với `[start, end)` (làm nổi bật trong bảng giá, spec 030 R4). */
    fun rulesOverlapping(rules: List<PriceRule>, date: LocalDate, start: TimeOfDay, end: TimeOfDay): List<PriceRule> {
        val day = DayConvention.fromDayOfWeek(date.dayOfWeek)
        return rules.filter { r ->
            val rs = TimeOfDay.parse(r.startTime)
            val re = TimeOfDay.parseEnd(r.endTime)
            day in r.daysOfWeek && rs != null && re != null && rs < end && start < re
        }
    }
}

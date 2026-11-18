package com.example.booking_app.feature.booking.domain

import com.example.booking_app.core.pricing.PriceVectors
import com.example.booking_app.core.pricing.SlotPricing
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.usecase.GenerateDailySlotsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.format.DateTimeFormatter

/**
 * Lưới giờ 040 phải ra cùng giá với thẻ kết quả 020 và chi tiết sân 030 (SC-002, 020 T036): chạy lưới trên
 * đúng bộ ca giá dùng chung `pricing/window-price-vectors.json`.
 */
class SharedSlotPricingTest {
    private val generate = GenerateDailySlotsUseCase()

    @Test
    fun gridPricesMatchTheSharedPriceVectors() {
        val cases = PriceVectors.load().cases
        assertEquals(9, cases.size)
        cases.forEach { c ->
            val rules = c.rules.map { PriceRule(it.id, it.daysOfWeek, it.startTime, it.endTime, it.pricePerHour, it.label) }
            val date = c.date.format(DateTimeFormatter.BASIC_ISO_DATE)
            val window = generate("court-1", date, "0000", "2400", rules, emptyList(), currentTimeMillis = 0L)
                .filter { it.startTime.toInt() in hhmm(c.start.minutes) until hhmm(c.end.minutes) }

            // Khung có slot không thuộc dòng giá nào là "Liên hệ" (null), giống SlotPricing.
            val total = if (window.any { it.blockReason == "NO_PRICE_RULE" }) null else window.sumOf { it.price }
            assertEquals("${c.id}", c.expectedTotal, total)
            assertEquals("${c.id} khớp core", SlotPricing.priceForWindow(c.rules, c.date, c.start, c.end), total)
        }
    }

    @Test
    fun emptyDaysOfWeekAppliesToNoDayLikeTheSharedPricing() {
        // Trước đây 040 coi daysOfWeek rỗng là mọi ngày, còn 020/030 coi là không ngày nào ("Liên hệ").
        val rules = listOf(PriceRule("r", emptyList(), "0500", "2300", 80_000, "NORMAL"))
        val slots = generate("court-1", "20261118", "0500", "0700", rules, emptyList(), currentTimeMillis = 0L)
        assertTrue(slots.all { it.status == SlotStatus.BLOCKED && it.blockReason == "NO_PRICE_RULE" && it.price == 0L })
    }

    private fun hhmm(minutes: Int) = (minutes / 60) * 100 + minutes % 60
}

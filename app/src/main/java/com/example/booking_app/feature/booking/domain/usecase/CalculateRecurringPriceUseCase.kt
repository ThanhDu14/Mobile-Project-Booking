package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.model.RecurringSummary
import javax.inject.Inject

class CalculateRecurringPriceUseCase @Inject constructor() {

    fun calculateSessionPrice(
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        priceRules: List<PriceRule>
    ): Long {
        val slotTimes = generateSlotTimes(startTime, endTime)
        var total = 0L

        for (slotStart in slotTimes) {
            val matchingRule = priceRules.firstOrNull { rule ->
                dayOfWeek in rule.daysOfWeek &&
                    rule.startTime <= slotStart &&
                    rule.endTime > slotStart
            }
            val slotPrice = matchingRule?.pricePerSlot ?: (priceRules.firstOrNull()?.pricePerSlot ?: 50_000L)
            total += slotPrice
        }
        return total
    }

    fun calculateDepositAmount(subtotal: Long): Long {
        val rawDeposit = (subtotal * 30L) / 100L
        return ((rawDeposit + 500L) / 1000L) * 1000L
    }

    fun calculateSummary(
        sessions: List<RecurringSession>
    ): RecurringSummary {
        val validSessions = sessions.filter {
            !it.isExcluded && it.status != RecurringSessionStatus.EXCLUDED
        }
        val subtotal = validSessions.sumOf { it.price }
        val deposit = calculateDepositAmount(subtotal)
        val canProceed = validSessions.size >= 4
        val warningMessage = if (!canProceed) {
            "Số buổi chơi sau khi xử lý xung đột còn ít hơn 4 buổi. Vui lòng chọn lại khung giờ hoặc chuyển sang đặt lẻ."
        } else null

        return RecurringSummary(
            totalSessions = validSessions.size,
            validSessions = validSessions,
            subtotalAmount = subtotal,
            depositAmount = deposit,
            canProceed = canProceed,
            warningMessage = warningMessage
        )
    }

    private fun generateSlotTimes(startTime: String, endTime: String): List<String> {
        val slots = mutableListOf<String>()
        var current = startTime.substring(0, 2).toInt() * 60 + startTime.substring(2, 4).toInt()
        val end = endTime.substring(0, 2).toInt() * 60 + endTime.substring(2, 4).toInt()

        while (current < end) {
            val h = current / 60
            val m = current % 60
            slots.add(String.format("%02d%02d", h, m))
            current += 30
        }
        return slots
    }
}

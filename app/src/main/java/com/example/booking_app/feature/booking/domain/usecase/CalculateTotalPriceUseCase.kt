package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.TimeSlot
import javax.inject.Inject

class CalculateTotalPriceUseCase @Inject constructor() {

    operator fun invoke(selectedSlots: List<TimeSlot>): Pair<Long, Long> {
        if (selectedSlots.isEmpty()) return Pair(0L, 0L)

        val totalPrice = selectedSlots.sumOf { it.price }
        val rawDeposit = (totalPrice * DEPOSIT_PERCENTAGE) / 100L
        val depositAmount = ((rawDeposit + ROUNDING_OFFSET) / ROUNDING_FACTOR) * ROUNDING_FACTOR

        return Pair(totalPrice, depositAmount)
    }

    companion object {
        const val DEPOSIT_PERCENTAGE = 30L
        const val ROUNDING_OFFSET = 500L
        const val ROUNDING_FACTOR = 1000L
    }
}

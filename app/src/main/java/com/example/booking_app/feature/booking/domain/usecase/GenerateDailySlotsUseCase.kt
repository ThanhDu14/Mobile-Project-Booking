package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject

class GenerateDailySlotsUseCase @Inject constructor() {

    operator fun invoke(
        courtId: String,
        date: String,
        openTime: String,
        closeTime: String,
        priceRules: List<PriceRule>,
        busySlots: List<TimeSlot>,
        selectedSlotIds: Set<String> = emptySet(),
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<TimeSlot> {
        val openMinutes = toMinutes(openTime)
        val closeMinutes = toMinutes(closeTime)
        if (openMinutes >= closeMinutes) return emptyList()

        val busyMap = busySlots.associateBy { it.slotId }
        val slots = mutableListOf<TimeSlot>()

        var currentMin = openMinutes
        while (currentMin < closeMinutes) {
            val nextMin = currentMin + SLOT_DURATION_MINUTES
            val startTime = formatTime(currentMin)
            val endTime = formatTime(nextMin)
            val slotId = "${courtId}_${date}_$startTime"

            // Determine slot price from PriceRules
            val matchedRule = priceRules.firstOrNull { rule ->
                val ruleStart = toMinutes(rule.startTime)
                val ruleEnd = toMinutes(rule.endTime)
                currentMin >= ruleStart && nextMin <= ruleEnd
            }
            val price = matchedRule?.pricePerSlot ?: 0L

            // Check busy slots or default to AVAILABLE
            val busy = busyMap[slotId]
            var status = busy?.status ?: SlotStatus.AVAILABLE
            var blockReason = busy?.blockReason
            var holdExpiresAtMillis = busy?.holdExpiresAtMillis

            // Check if user has this slot currently selected
            if (status == SlotStatus.AVAILABLE && selectedSlotIds.contains(slotId)) {
                status = SlotStatus.SELECTED
            }

            // Check if slot has passed for current date
            if (currentTimeMillis > 0L && status == SlotStatus.AVAILABLE) {
                val slotStartMillis = calculateSlotStartMillis(date, startTime)
                if (slotStartMillis <= currentTimeMillis) {
                    status = SlotStatus.PAST
                }
            }

            slots.add(
                TimeSlot(
                    slotId = slotId,
                    courtId = courtId,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    price = price,
                    status = status,
                    blockReason = blockReason,
                    holdExpiresAtMillis = holdExpiresAtMillis
                )
            )

            currentMin = nextMin
        }

        return slots
    }

    private fun toMinutes(hhmm: String): Int {
        val h = hhmm.substring(0, 2).toIntOrNull() ?: 0
        val m = hhmm.substring(2, 4).toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.US, "%02d%02d", h, m)
    }

    private fun calculateSlotStartMillis(date: String, startTime: String): Long {
        return try {
            val year = date.substring(0, 4).toInt()
            val month = date.substring(4, 6).toInt()
            val day = date.substring(6, 8).toInt()
            val hour = startTime.substring(0, 2).toInt()
            val minute = startTime.substring(2, 4).toInt()
            ZonedDateTime.of(
                year, month, day, hour, minute, 0, 0,
                ZONE_VIETNAM
            ).toInstant().toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    companion object {
        const val SLOT_DURATION_MINUTES = 30
        val ZONE_VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    }
}

package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject

/**
 * P1-2: Index bảng giá theo (dayOfWeek, time) -> PriceRule để tra cứu O(1) cho mỗi slot 30 phút.
 * Giữ nguyên 100% ngữ nghĩa matching của firstOrNull:
 * - daysOfWeek rỗng = mọi ngày trong tuần;
 * - slot nằm trọn trong [startTime, endTime];
 * - nếu nhiều rule cùng khớp thì rule đầu tiên trong list thắng;
 * - không rule nào khớp thì giá 0 và slot thành BLOCKED với reason NO_PRICE_RULE.
 */
class IndexedPriceRules(
    private val rulesBySlotIndex: Array<PriceRule?>
) {
    fun getRule(slotStartMinutes: Int): PriceRule? {
        val index = slotStartMinutes / GenerateDailySlotsUseCase.SLOT_DURATION_MINUTES
        return if (index in rulesBySlotIndex.indices) rulesBySlotIndex[index] else null
    }

    companion object {
        fun from(priceRules: List<PriceRule>, isoDayOfWeek: Int): IndexedPriceRules {
            if (priceRules.isEmpty()) {
                return IndexedPriceRules(emptyArray())
            }

            // Parse trước giờ bắt đầu/kết thúc (phút) của từng rule một lần duy nhất
            val parsedRules = priceRules.map { rule ->
                val dayMatch = rule.daysOfWeek.isEmpty() || rule.daysOfWeek.contains(isoDayOfWeek)
                val start = toMinutes(rule.startTime)
                val end = toMinutes(rule.endTime)
                Triple(rule, dayMatch, start to end)
            }

            // 48 slot trong 24 giờ (mỗi slot 30 phút: 00:00 -> 24:00)
            val table = Array<PriceRule?>(48) { slotIndex ->
                val currentMin = slotIndex * GenerateDailySlotsUseCase.SLOT_DURATION_MINUTES
                val nextMin = currentMin + GenerateDailySlotsUseCase.SLOT_DURATION_MINUTES
                parsedRules.firstOrNull { (rule, dayMatch, range) ->
                    dayMatch && currentMin >= range.first && nextMin <= range.second
                }?.first
            }
            return IndexedPriceRules(table)
        }

        private fun toMinutes(hhmm: String): Int {
            val h = hhmm.substring(0, 2).toIntOrNull() ?: 0
            val m = hhmm.substring(2, 4).toIntOrNull() ?: 0
            return h * 60 + m
        }
    }
}

class GenerateDailySlotsUseCase @Inject constructor() {

    fun indexPriceRules(priceRules: List<PriceRule>, isoDayOfWeek: Int): IndexedPriceRules {
        return IndexedPriceRules.from(priceRules, isoDayOfWeek)
    }

    fun parseIsoDayOfWeek(date: String): Int {
        return try {
            val y = date.substring(0, 4).toInt()
            val m = date.substring(4, 6).toInt()
            val d = date.substring(6, 8).toInt()
            LocalDate.of(y, m, d).dayOfWeek.value // 1 = Monday .. 7 = Sunday
        } catch (e: Exception) {
            1
        }
    }

    operator fun invoke(
        courtId: String,
        date: String,
        openTime: String,
        closeTime: String,
        priceRules: List<PriceRule>,
        busySlots: List<TimeSlot>,
        selectedSlotIds: Set<String> = emptySet(),
        currentTimeMillis: Long = System.currentTimeMillis(),
        precomputedRules: IndexedPriceRules? = null
    ): List<TimeSlot> {
        val openMinutes = toMinutes(openTime)
        val closeMinutes = toMinutes(closeTime)
        if (openMinutes >= closeMinutes) return emptyList()

        val isoDayOfWeek = parseIsoDayOfWeek(date)
        val indexedRules = precomputedRules ?: indexPriceRules(priceRules, isoDayOfWeek)

        val busyMap = busySlots.associateBy { it.slotId }
        val slots = mutableListOf<TimeSlot>()

        var currentMin = openMinutes
        while (currentMin < closeMinutes) {
            val nextMin = currentMin + SLOT_DURATION_MINUTES
            val startTime = formatTime(currentMin)
            val endTime = formatTime(nextMin)
            val slotId = "${courtId}_${date}_$startTime"

            // P1-2: Tra cứu O(1) từ indexedRules
            val matchedRule = indexedRules.getRule(currentMin)
            val isOutOfPriceRule = matchedRule == null
            val price = matchedRule?.pricePerSlot ?: 0L

            // Check busy slots or default to AVAILABLE (or BLOCKED if no rule matches)
            val busy = busyMap[slotId]
            var status = busy?.status ?: if (isOutOfPriceRule) SlotStatus.BLOCKED else SlotStatus.AVAILABLE
            var blockReason = busy?.blockReason ?: if (isOutOfPriceRule) "NO_PRICE_RULE" else null
            var holdExpiresAtMillis = busy?.holdExpiresAtMillis

            // P0-4: Xử lý slot HELD đã hết hạn (chống zombie HELD)
            if (status == SlotStatus.HELD && holdExpiresAtMillis != null && holdExpiresAtMillis <= currentTimeMillis) {
                status = if (isOutOfPriceRule) SlotStatus.BLOCKED else SlotStatus.AVAILABLE
                blockReason = if (isOutOfPriceRule) "NO_PRICE_RULE" else null
                holdExpiresAtMillis = null
            }

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

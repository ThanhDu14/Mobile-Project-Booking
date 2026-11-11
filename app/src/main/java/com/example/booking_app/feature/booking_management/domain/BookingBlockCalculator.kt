package com.example.booking_app.feature.booking_management.domain

import java.text.SimpleDateFormat
import java.util.Date

data class BookingTimeBlock(
    val startAtMillis: Long,
    val endAtMillis: Long
) {
    val durationMinutes: Int get() = ((endAtMillis - startAtMillis) / 60_000L).toInt()
}

data class BookingBlockSummary(
    val blocks: List<BookingTimeBlock>,
    val totalPlayingMinutes: Int,
    val totalGapMinutes: Int,
    val spanMinutes: Int
) {
    fun formatPlayingDuration(): String = formatMinutes(totalPlayingMinutes)

    fun formatGapNote(): String? {
        if (blocks.size <= 1 || totalGapMinutes <= 0) return null
        return "Nghỉ ${formatMinutes(totalGapMinutes)} giữa giờ"
    }

    fun formatTimeRanges(timeFormat: SimpleDateFormat): String {
        return blocks.joinToString(", ") { block ->
            "${timeFormat.format(Date(block.startAtMillis))} – ${timeFormat.format(Date(block.endAtMillis))}"
        }
    }

    companion object {
        fun formatMinutes(minutes: Int): String {
            val hours = minutes / 60
            val remain = minutes % 60
            return when {
                hours > 0 && remain > 0 -> "$hours giờ $remain phút"
                hours > 0 -> "$hours giờ"
                else -> "$remain phút"
            }
        }
    }
}

object BookingBlockCalculator {
    fun calculateBlocks(items: List<BookingSlotItem>): BookingBlockSummary {
        if (items.isEmpty()) {
            return BookingBlockSummary(emptyList(), 0, 0, 0)
        }
        val sorted = items.sortedBy { it.startAtMillis }
        val blocks = mutableListOf<BookingTimeBlock>()
        var currentStart = sorted[0].startAtMillis
        var currentEnd = sorted[0].endAtMillis

        for (i in 1 until sorted.size) {
            val item = sorted[i]
            if (item.startAtMillis <= currentEnd) {
                currentEnd = maxOf(currentEnd, item.endAtMillis)
            } else {
                blocks.add(BookingTimeBlock(currentStart, currentEnd))
                currentStart = item.startAtMillis
                currentEnd = item.endAtMillis
            }
        }
        blocks.add(BookingTimeBlock(currentStart, currentEnd))

        val totalPlayingMinutes = blocks.sumOf { it.durationMinutes }
        val overallStart = blocks.first().startAtMillis
        val overallEnd = blocks.last().endAtMillis
        val spanMinutes = ((overallEnd - overallStart) / 60_000L).toInt()
        val totalGapMinutes = maxOf(0, spanMinutes - totalPlayingMinutes)

        return BookingBlockSummary(
            blocks = blocks,
            totalPlayingMinutes = totalPlayingMinutes,
            totalGapMinutes = totalGapMinutes,
            spanMinutes = spanMinutes
        )
    }
}

package com.example.booking_app.feature.booking_management.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class BookingBlockCalculatorTest {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun createItem(
        slotId: String,
        courtName: String = "Sân 4 · PU",
        startEpoch: Long,
        endEpoch: Long,
        price: Long = 45_000L
    ) = BookingSlotItem(
        slotId = slotId,
        courtId = "court-04",
        courtName = courtName,
        startAtMillis = startEpoch,
        endAtMillis = endEpoch,
        price = price
    )

    // Base time: 20:00 on 2026-11-09 (epoch ms: 1794229200000L)
    private val t2000 = 1794229200000L
    private val t2030 = t2000 + 30 * 60_000L
    private val t2100 = t2000 + 60 * 60_000L
    private val t2130 = t2000 + 90 * 60_000L
    private val t2200 = t2000 + 120 * 60_000L
    private val t2230 = t2000 + 150 * 60_000L
    private val t2300 = t2000 + 180 * 60_000L
    private val t2330 = t2000 + 210 * 60_000L

    @Test
    fun `1 block with 3 consecutive slots`() {
        val items = listOf(
            createItem("s1", startEpoch = t2000, endEpoch = t2030),
            createItem("s2", startEpoch = t2030, endEpoch = t2100),
            createItem("s3", startEpoch = t2100, endEpoch = t2130)
        )

        val summary = BookingBlockCalculator.calculateBlocks(items)

        assertEquals(1, summary.blocks.size)
        assertEquals(t2000, summary.blocks[0].startAtMillis)
        assertEquals(t2130, summary.blocks[0].endAtMillis)
        assertEquals(90, summary.totalPlayingMinutes)
        assertEquals(0, summary.totalGapMinutes)
        assertEquals(90, summary.spanMinutes)
        assertEquals("1 giờ 30 phút", summary.formatPlayingDuration())
        assertNull(summary.formatGapNote())
    }

    @Test
    fun `2 blocks with 1-hour gap between them`() {
        // Block 1: 20:00 - 21:30 (3 slots = 90 mins)
        // Gap: 21:30 - 22:30 (60 mins = 1 hour)
        // Block 2: 22:30 - 23:30 (2 slots = 60 mins)
        val items = listOf(
            createItem("s1", startEpoch = t2000, endEpoch = t2030),
            createItem("s2", startEpoch = t2030, endEpoch = t2100),
            createItem("s3", startEpoch = t2100, endEpoch = t2130),
            createItem("s4", startEpoch = t2230, endEpoch = t2300),
            createItem("s5", startEpoch = t2300, endEpoch = t2330)
        )

        val summary = BookingBlockCalculator.calculateBlocks(items)

        assertEquals(2, summary.blocks.size)
        assertEquals(t2000, summary.blocks[0].startAtMillis)
        assertEquals(t2130, summary.blocks[0].endAtMillis)
        assertEquals(t2230, summary.blocks[1].startAtMillis)
        assertEquals(t2330, summary.blocks[1].endAtMillis)

        assertEquals(150, summary.totalPlayingMinutes) // 90 + 60 = 150 mins = 2h30m
        assertEquals(60, summary.totalGapMinutes)      // 210 - 150 = 60 mins = 1h
        assertEquals(210, summary.spanMinutes)         // 20:00 to 23:30 = 210 mins = 3h30m

        assertEquals("2 giờ 30 phút", summary.formatPlayingDuration())
        assertEquals("Nghỉ 1 giờ giữa giờ", summary.formatGapNote())
    }

    @Test
    fun `3 blocks separated by gaps`() {
        // Block 1: 20:00 - 20:30 (30m)
        // Gap 1: 20:30 - 21:30 (60m)
        // Block 2: 21:30 - 22:00 (30m)
        // Gap 2: 22:00 - 23:00 (60m)
        // Block 3: 23:00 - 23:30 (30m)
        val items = listOf(
            createItem("s1", startEpoch = t2000, endEpoch = t2030),
            createItem("s2", startEpoch = t2130, endEpoch = t2200),
            createItem("s3", startEpoch = t2300, endEpoch = t2330)
        )

        val summary = BookingBlockCalculator.calculateBlocks(items)

        assertEquals(3, summary.blocks.size)
        assertEquals(90, summary.totalPlayingMinutes)
        assertEquals(120, summary.totalGapMinutes)
        assertEquals(210, summary.spanMinutes)
        assertEquals("1 giờ 30 phút", summary.formatPlayingDuration())
        assertEquals("Nghỉ 2 giờ giữa giờ", summary.formatGapNote())
    }

    @Test
    fun `items unsorted in input list are correctly sorted and merged`() {
        val items = listOf(
            createItem("s5", startEpoch = t2300, endEpoch = t2330),
            createItem("s1", startEpoch = t2000, endEpoch = t2030),
            createItem("s4", startEpoch = t2230, endEpoch = t2300),
            createItem("s3", startEpoch = t2100, endEpoch = t2130),
            createItem("s2", startEpoch = t2030, endEpoch = t2100)
        )

        val summary = BookingBlockCalculator.calculateBlocks(items)

        assertEquals(2, summary.blocks.size)
        assertEquals(t2000, summary.blocks[0].startAtMillis)
        assertEquals(t2130, summary.blocks[0].endAtMillis)
        assertEquals(t2230, summary.blocks[1].startAtMillis)
        assertEquals(t2330, summary.blocks[1].endAtMillis)
        assertEquals(150, summary.totalPlayingMinutes)
    }

    @Test
    fun `adjacent blocks are merged into one continuous block`() {
        // Block A: 20:00 - 21:00
        // Block B: 21:00 - 22:00 (starts exactly when A ends)
        val items = listOf(
            createItem("s1", startEpoch = t2000, endEpoch = t2030),
            createItem("s2", startEpoch = t2030, endEpoch = t2100),
            createItem("s3", startEpoch = t2100, endEpoch = t2130),
            createItem("s4", startEpoch = t2130, endEpoch = t2200)
        )

        val summary = BookingBlockCalculator.calculateBlocks(items)

        assertEquals(1, summary.blocks.size)
        assertEquals(t2000, summary.blocks[0].startAtMillis)
        assertEquals(t2200, summary.blocks[0].endAtMillis)
        assertEquals(120, summary.totalPlayingMinutes)
        assertEquals(0, summary.totalGapMinutes)
        assertEquals("2 giờ", summary.formatPlayingDuration())
        assertNull(summary.formatGapNote())
    }

    @Test
    fun `empty items returns zeroed summary`() {
        val summary = BookingBlockCalculator.calculateBlocks(emptyList())
        assertEquals(0, summary.blocks.size)
        assertEquals(0, summary.totalPlayingMinutes)
        assertEquals(0, summary.totalGapMinutes)
        assertEquals("0 phút", summary.formatPlayingDuration())
        assertNull(summary.formatGapNote())
    }
}

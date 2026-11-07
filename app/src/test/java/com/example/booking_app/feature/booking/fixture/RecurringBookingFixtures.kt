package com.example.booking_app.feature.booking.fixture

import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot

object RecurringBookingFixtures {

    fun sampleCourts(): List<Court> = listOf(
        Court(id = "court-01", name = "Sân 1", surfaceType = "PU", isActive = true, sortOrder = 1),
        Court(id = "court-02", name = "Sân 2", surfaceType = "PU", isActive = true, sortOrder = 2),
        Court(id = "court-03", name = "Sân 3", surfaceType = "WOOD", isActive = true, sortOrder = 3),
        Court(id = "court-04", name = "Sân 4", surfaceType = "MAT", isActive = true, sortOrder = 4)
    )

    fun samplePriceRules(): List<PriceRule> = listOf(
        PriceRule(
            id = "rule-weekday-regular",
            daysOfWeek = listOf(1, 2, 3, 4, 5),
            startTime = "0500",
            endTime = "1600",
            pricePerHour = 60_000L,
            label = "Giờ thường ngày thường"
        ),
        PriceRule(
            id = "rule-weekday-peak",
            daysOfWeek = listOf(1, 2, 3, 4, 5),
            startTime = "1600",
            endTime = "2400",
            pricePerHour = 100_000L,
            label = "Giờ cao điểm ngày thường"
        ),
        PriceRule(
            id = "rule-weekend",
            daysOfWeek = listOf(6, 7),
            startTime = "0500",
            endTime = "2400",
            pricePerHour = 120_000L,
            label = "Cuối tuần"
        )
    )

    fun sampleConfig(
        venueId: String = "venue-01",
        courtId: String = "court-01",
        daysOfWeek: List<Int> = listOf(2, 4), // Thứ 3, Thứ 5
        startTime: String = "1800",
        endTime: String = "2000",
        totalWeeks: Int = 4,
        startNextWeek: Boolean = true
    ): RecurringConfig = RecurringConfig(
        venueId = venueId,
        courtId = courtId,
        daysOfWeek = daysOfWeek,
        startTime = startTime,
        endTime = endTime,
        totalWeeks = totalWeeks,
        startNextWeek = startNextWeek
    )

    fun sampleBusySlot(
        courtId: String,
        date: String,
        startTime: String,
        status: SlotStatus = SlotStatus.BOOKED,
        price: Long = 50_000L
    ): TimeSlot = TimeSlot(
        slotId = "${courtId}_${date}_$startTime",
        courtId = courtId,
        date = date,
        startTime = startTime,
        endTime = formatSlotEndTime(startTime),
        price = price,
        status = status
    )

    private fun formatSlotEndTime(startTime: String): String {
        val h = startTime.substring(0, 2).toInt()
        val m = startTime.substring(2, 4).toInt()
        val totalM = h * 60 + m + 30
        return String.format("%02d%02d", totalM / 60, totalM % 60)
    }
}

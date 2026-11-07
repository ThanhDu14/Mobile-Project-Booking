package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.usecase.CalculateTotalPriceUseCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CalculateTotalPriceUseCaseTest {

    private lateinit var useCase: CalculateTotalPriceUseCase

    private fun createSlot(price: Long) = TimeSlot(
        slotId = "court-01_20261027_0600",
        courtId = "court-01",
        date = "20261027",
        startTime = "0600",
        endTime = "0630",
        price = price,
        status = SlotStatus.SELECTED
    )

    @Before
    fun setUp() {
        useCase = CalculateTotalPriceUseCase()
    }

    @Test
    fun calculateTotal_normalHoursRate() {
        // 2 slots in normal hours (40k each)
        val slots = listOf(createSlot(40_000L), createSlot(40_000L))
        val (total, deposit) = useCase(slots)

        assertEquals(80_000L, total)
        // 30% of 80,000 = 24,000
        assertEquals(24_000L, deposit)
    }

    @Test
    fun calculateTotal_peakHoursRate() {
        // 2 slots in peak hours (75k each)
        val slots = listOf(createSlot(75_000L), createSlot(75_000L))
        val (total, deposit) = useCase(slots)

        assertEquals(150_000L, total)
        // 30% of 150,000 = 45,000
        assertEquals(45_000L, deposit)
    }

    @Test
    fun calculateTotal_boundaryTransitionCrossesNormalAndPeak() {
        // 16:30-17:00 (Normal 40k) and 17:00-17:30 (Peak 75k)
        val slots = listOf(createSlot(40_000L), createSlot(75_000L))
        val (total, deposit) = useCase(slots)

        assertEquals(115_000L, total)
        // 30% of 115,000 = 34,500 -> ((34,500 + 500) / 1000) * 1000 = 35,000
        assertEquals(35_000L, deposit)
    }

    @Test
    fun calculateDeposit_roundingEdgeCases() {
        // 75,000 VND total (single peak slot)
        val slots1 = listOf(createSlot(75_000L))
        val (_, deposit1) = useCase(slots1)
        // 30% of 75,000 = 22,500 -> rounded to 23,000
        assertEquals(23_000L, deposit1)

        // Empty slots
        val (totalEmpty, depositEmpty) = useCase(emptyList())
        assertEquals(0L, totalEmpty)
        assertEquals(0L, depositEmpty)
    }
}

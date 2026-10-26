package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.usecase.GenerateDailySlotsUseCase
import com.example.booking_app.feature.booking.fixture.BookingSlotFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GenerateDailySlotsUseCaseTest {

    private lateinit var useCase: GenerateDailySlotsUseCase

    private val courtId = "court-01"
    private val date = "20261027" // Future or test date

    private val normalRule = BookingSlotFixtures.createNormalPriceRule(
        startTime = "0600",
        endTime = "1700",
        pricePerHour = 80_000L
    )

    private val peakRule = BookingSlotFixtures.createPeakPriceRule(
        startTime = "1700",
        endTime = "2200",
        pricePerHour = 150_000L
    )

    private val priceRules = listOf(normalRule, peakRule)

    @Before
    fun setUp() {
        useCase = GenerateDailySlotsUseCase()
    }

    @Test
    fun generateDailySlots_from0600to2200_producesExactly32Slots() {
        val slots = useCase(
            courtId = courtId,
            date = date,
            openTime = "0600",
            closeTime = "2200",
            priceRules = priceRules,
            busySlots = emptyList(),
            currentTimeMillis = 0L // Past check won't trigger for epoch 0
        )

        // 16 hours * 2 slots/hour = 32 slots
        assertEquals(32, slots.size)
        assertEquals("0600", slots.first().startTime)
        assertEquals("0630", slots.first().endTime)
        assertEquals("2130", slots.last().startTime)
        assertEquals("2200", slots.last().endTime)
        assertEquals(SlotStatus.AVAILABLE, slots.first().status)
        assertEquals(40_000L, slots.first().price) // Normal slot
    }

    @Test
    fun generateDailySlots_mergesBusySlotsFromFirestore() {
        val busyBooked = TimeSlot(
            slotId = "${courtId}_${date}_0800",
            courtId = courtId,
            date = date,
            startTime = "0800",
            endTime = "0830",
            price = 40_000L,
            status = SlotStatus.BOOKED
        )
        val busyHeld = TimeSlot(
            slotId = "${courtId}_${date}_0830",
            courtId = courtId,
            date = date,
            startTime = "0830",
            endTime = "0900",
            price = 40_000L,
            status = SlotStatus.HELD,
            holdExpiresAtMillis = 1729000000000L
        )

        val slots = useCase(
            courtId = courtId,
            date = date,
            openTime = "0600",
            closeTime = "2200",
            priceRules = priceRules,
            busySlots = listOf(busyBooked, busyHeld),
            currentTimeMillis = 0L
        )

        val slot0800 = slots.find { it.startTime == "0800" }
        assertNotNull(slot0800)
        assertEquals(SlotStatus.BOOKED, slot0800?.status)

        val slot0830 = slots.find { it.startTime == "0830" }
        assertNotNull(slot0830)
        assertEquals(SlotStatus.HELD, slot0830?.status)

        val slot0900 = slots.find { it.startTime == "0900" }
        assertNotNull(slot0900)
        assertEquals(SlotStatus.AVAILABLE, slot0900?.status)
    }

    @Test
    fun generateDailySlots_assignsPastStatus_forElapsedSlotsToday() {
        val todayStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        // Current time is 10:15 AM today
        val tenFifteenToday = LocalDateTime.now().withHour(10).withMinute(15)
            .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli()

        val slots = useCase(
            courtId = courtId,
            date = todayStr,
            openTime = "0600",
            closeTime = "2200",
            priceRules = priceRules,
            busySlots = emptyList(),
            currentTimeMillis = tenFifteenToday
        )

        // Slot 0600-0630, 0930-1000, 1000-1030
        // Slots starting <= 10:15 (i.e. <= 10:00) should be PAST
        val slot0600 = slots.find { it.startTime == "0600" }
        assertEquals(SlotStatus.PAST, slot0600?.status)

        val slot1000 = slots.find { it.startTime == "1000" }
        assertEquals(SlotStatus.PAST, slot1000?.status)

        // Slot 1030 starts after 10:15 -> AVAILABLE
        val slot1030 = slots.find { it.startTime == "1030" }
        assertEquals(SlotStatus.AVAILABLE, slot1030?.status)
    }

    @Test
    fun generateDailySlots_assignsBlockedDropIn_forSocialDropInSession() {
        val dropInSlot = TimeSlot(
            slotId = "${courtId}_${date}_1900",
            courtId = courtId,
            date = date,
            startTime = "1900",
            endTime = "1930",
            price = 75_000L,
            status = SlotStatus.BLOCKED,
            blockReason = "DROP_IN"
        )

        val slots = useCase(
            courtId = courtId,
            date = date,
            openTime = "0600",
            closeTime = "2200",
            priceRules = priceRules,
            busySlots = listOf(dropInSlot),
            currentTimeMillis = 0L
        )

        val slot1900 = slots.find { it.startTime == "1900" }
        assertNotNull(slot1900)
        assertEquals(SlotStatus.BLOCKED, slot1900?.status)
        assertEquals("DROP_IN", slot1900?.blockReason)
    }
}

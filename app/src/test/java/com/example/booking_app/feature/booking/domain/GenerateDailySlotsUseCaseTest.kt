package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.usecase.GenerateDailySlotsUseCase
import com.example.booking_app.feature.booking.fixture.BookingSlotFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun generateDailySlots_whenNoPriceRuleMatches_assignsBlockedNoPriceRule() {
        // Q11: Slot không khớp rule nào -> không bán, đánh dấu BLOCKED với NO_PRICE_RULE
        // 2026-10-31 là Thứ Bảy (dayOfWeek = 6), trong khi priceRules chỉ áp dụng Mon-Fri (1..5)
        val saturdayDate = "20261031"
        val slots = useCase(
            courtId = courtId,
            date = saturdayDate,
            openTime = "0600",
            closeTime = "0800",
            priceRules = priceRules, // Mon-Fri only
            busySlots = emptyList(),
            currentTimeMillis = 0L
        )

        assertEquals(4, slots.size)
        assertTrue(slots.all { it.status == SlotStatus.BLOCKED && it.blockReason == "NO_PRICE_RULE" })
    }

    @Test
    fun generateDailySlots_whenHeldSlotExpired_treatedAsAvailable() {
        val now = 1_700_000_000_000L
        val expiredHeldSlot = TimeSlot(
            slotId = "${courtId}_${date}_0830",
            courtId = courtId,
            date = date,
            startTime = "0830",
            endTime = "0900",
            price = 40_000L,
            status = SlotStatus.HELD,
            holdExpiresAtMillis = now - 1000L // Hết hạn 1 giây trước
        )

        val slots = useCase(
            courtId = courtId,
            date = date,
            openTime = "0600",
            closeTime = "2200",
            priceRules = priceRules,
            busySlots = listOf(expiredHeldSlot),
            currentTimeMillis = now
        )

        val slot0830 = slots.find { it.startTime == "0830" }
        assertNotNull(slot0830)
        assertEquals(SlotStatus.AVAILABLE, slot0830?.status)
    }

    @Test
    fun generateDailySlots_whenHeldSlotStillValid_treatedAsHeld() {
        val now = 1_700_000_000_000L
        val activeHeldSlot = TimeSlot(
            slotId = "${courtId}_${date}_0830",
            courtId = courtId,
            date = date,
            startTime = "0830",
            endTime = "0900",
            price = 40_000L,
            status = SlotStatus.HELD,
            holdExpiresAtMillis = now + 60_000L // Còn hạn 1 phút
        )

        val slots = useCase(
            courtId = courtId,
            date = date,
            openTime = "0600",
            closeTime = "2200",
            priceRules = priceRules,
            busySlots = listOf(activeHeldSlot),
            currentTimeMillis = now
        )

        val slot0830 = slots.find { it.startTime == "0830" }
        assertNotNull(slot0830)
        assertEquals(SlotStatus.HELD, slot0830?.status)
    }

    @Test
    fun goldenTest_indexedRulesMatchesFirstOrNullExactSemantics() {
        // Test golden: Cùng input (rule chồng lấn, daysOfWeek rỗng / cụ thể, khoảng không có rule)
        // đảm bảo output dùng indexed rules khớp 100% với ngữ nghĩa firstOrNull tuyến tính
        val ruleAllDays = PriceRule("r_all", emptyList(), "0600", "1200", 60_000L, "Sáng")
        val ruleOverlapping1 = PriceRule("r_overlap_1", listOf(2), "1200", "1800", 80_000L, "Thắng vì đứng trước")
        val ruleOverlapping2 = PriceRule("r_overlap_2", listOf(2), "1200", "2000", 120_000L, "Thua vì đứng sau")
        val ruleNight = PriceRule("r_night", listOf(2), "2000", "2200", 100_000L, "Tối")
        // 18:00 - 20:00: ruleOverlapping2 vẫn khớp
        // 22:00 - 24:00: Không rule nào khớp -> BLOCKED NO_PRICE_RULE

        val testRules = listOf(ruleAllDays, ruleOverlapping1, ruleOverlapping2, ruleNight)
        // 2026-10-27 là Thứ 3 (isoDayOfWeek = 2)
        val testDate = "20261027"

        val slots = useCase(
            courtId = courtId,
            date = testDate,
            openTime = "0600",
            closeTime = "2400",
            priceRules = testRules,
            busySlots = emptyList(),
            currentTimeMillis = 0L
        )

        // 1. Kiểm tra slot sáng (0600-0630): khớp r_all (daysOfWeek rỗng)
        val slot0600 = slots.find { it.startTime == "0600" }!!
        assertEquals(30_000L, slot0600.price)
        assertEquals(SlotStatus.AVAILABLE, slot0600.status)

        // 2. Kiểm tra slot chồng lấn (1200-1230): cả r_overlap_1 và r_overlap_2 cùng khớp, r_overlap_1 thắng
        val slot1200 = slots.find { it.startTime == "1200" }!!
        assertEquals(40_000L, slot1200.price) // 80_000 / 2
        assertEquals(SlotStatus.AVAILABLE, slot1200.status)

        // 3. Kiểm tra slot 1800-1830: r_overlap_1 hết hạn, r_overlap_2 thắng
        val slot1800 = slots.find { it.startTime == "1800" }!!
        assertEquals(60_000L, slot1800.price) // 120_000 / 2
        assertEquals(SlotStatus.AVAILABLE, slot1800.status)

        // 4. Kiểm tra slot tối (2000-2030): r_night thắng
        val slot2000 = slots.find { it.startTime == "2000" }!!
        assertEquals(50_000L, slot2000.price) // 100_000 / 2

        // 5. Kiểm tra slot đêm (2200-2230): Không rule nào khớp -> BLOCKED với NO_PRICE_RULE
        val slot2200 = slots.find { it.startTime == "2200" }!!
        assertEquals(0L, slot2200.price)
        assertEquals(SlotStatus.BLOCKED, slot2200.status)
        assertEquals("NO_PRICE_RULE", slot2200.blockReason)
    }
}

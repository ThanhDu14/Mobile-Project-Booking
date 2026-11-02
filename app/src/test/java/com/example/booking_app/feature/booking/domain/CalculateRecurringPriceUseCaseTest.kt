package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.usecase.CalculateRecurringPriceUseCase
import com.example.booking_app.feature.booking.fixture.RecurringBookingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CalculateRecurringPriceUseCaseTest {

    private lateinit var useCase: CalculateRecurringPriceUseCase
    private val priceRules = RecurringBookingFixtures.samplePriceRules()

    @Before
    fun setUp() {
        useCase = CalculateRecurringPriceUseCase()
    }

    @Test
    fun calculateSessionPrice_weekdayPeakVsRegular() {
        // Thứ 3 (dayOfWeek = 2): 18:00 - 20:00 (4 slots @ 50k/slot = 100k/h) -> 200.000 đ
        val weekdayPrice = useCase.calculateSessionPrice(
            dayOfWeek = 2,
            startTime = "1800",
            endTime = "2000",
            priceRules = priceRules
        )
        assertEquals(200_000L, weekdayPrice)

        // Thứ 3 (dayOfWeek = 2): 08:00 - 10:00 (4 slots @ 30k/slot = 60k/h) -> 120.000 đ
        val morningPrice = useCase.calculateSessionPrice(
            dayOfWeek = 2,
            startTime = "0800",
            endTime = "1000",
            priceRules = priceRules
        )
        assertEquals(120_000L, morningPrice)
    }

    @Test
    fun calculateSessionPrice_weekendRulesAppliedCorrectly() {
        // Chủ nhật (dayOfWeek = 7): 18:00 - 20:00 (4 slots @ 60k/slot = 120k/h) -> 240.000 đ
        // Khác biệt rõ rệt so với ngày thường 200.000 đ, kiểm chứng price matching theo daysOfWeek
        val weekendPrice = useCase.calculateSessionPrice(
            dayOfWeek = 7,
            startTime = "1800",
            endTime = "2000",
            priceRules = priceRules
        )
        assertEquals(240_000L, weekendPrice)
    }

    @Test
    fun calculateDepositAmount_roundsToNearestThousand() {
        // 150.000 đ * 30% = 45.000 đ
        assertEquals(45_000L, useCase.calculateDepositAmount(150_000L))

        // 151.000 đ * 30% = 45.300 đ -> làm tròn thành 45.000 đ
        assertEquals(45_000L, useCase.calculateDepositAmount(151_000L))

        // 152.000 đ * 30% = 45.600 đ -> làm tròn thành 46.000 đ
        assertEquals(46_000L, useCase.calculateDepositAmount(152_000L))

        // 3.840.000 đ (trong mockup) * 30% = 1.152.000 đ
        assertEquals(1_152_000L, useCase.calculateDepositAmount(3_840_000L))
    }

    @Test
    fun calculateSummary_excludesSkippedSessionsAndCalculatesAccurateTotals() {
        val sessions = listOf(
            RecurringSession(
                date = LocalDate.of(2026, 11, 3),
                startTime = "1800",
                endTime = "2000",
                courtId = "court-01",
                courtName = "Sân 1",
                slotIds = listOf("s1", "s2"),
                price = 200_000L,
                status = RecurringSessionStatus.AVAILABLE
            ),
            RecurringSession(
                date = LocalDate.of(2026, 11, 5),
                startTime = "1800",
                endTime = "2000",
                courtId = "court-01",
                courtName = "Sân 1",
                slotIds = listOf("s3", "s4"),
                price = 200_000L,
                status = RecurringSessionStatus.AVAILABLE
            ),
            RecurringSession(
                date = LocalDate.of(2026, 11, 10),
                startTime = "1800",
                endTime = "2000",
                courtId = "court-01",
                courtName = "Sân 1",
                slotIds = listOf("s5", "s6"),
                price = 200_000L,
                status = RecurringSessionStatus.EXCLUDED, // Đã bỏ qua
                isExcluded = true
            ),
            RecurringSession(
                date = LocalDate.of(2026, 11, 12),
                startTime = "1800",
                endTime = "2000",
                courtId = "court-01",
                courtName = "Sân 1",
                slotIds = listOf("s7", "s8"),
                price = 200_000L,
                status = RecurringSessionStatus.AVAILABLE
            ),
            RecurringSession(
                date = LocalDate.of(2026, 11, 17),
                startTime = "1800",
                endTime = "2000",
                courtId = "court-01",
                courtName = "Sân 1",
                slotIds = listOf("s9", "s10"),
                price = 200_000L,
                status = RecurringSessionStatus.AVAILABLE
            )
        )

        val summary = useCase.calculateSummary(sessions)
        // 5 buổi ban đầu nhưng 1 buổi bị loại bỏ -> 4 buổi hợp lệ
        assertEquals(4, summary.totalSessions)
        // 4 * 200.000 = 800.000 đ
        assertEquals(800_000L, summary.subtotalAmount)
        // Cọc 30%: 800.000 * 30% = 240.000 đ
        assertEquals(240_000L, summary.depositAmount)
        assertTrue(summary.canProceed)
    }
}

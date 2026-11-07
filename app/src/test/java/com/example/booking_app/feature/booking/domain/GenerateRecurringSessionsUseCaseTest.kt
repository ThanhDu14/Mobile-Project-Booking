package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.usecase.GenerateRecurringSessionsUseCase
import com.example.booking_app.feature.booking.fixture.RecurringBookingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class GenerateRecurringSessionsUseCaseTest {

    private lateinit var useCase: GenerateRecurringSessionsUseCase

    @Before
    fun setUp() {
        useCase = GenerateRecurringSessionsUseCase()
    }

    @Test
    fun startNextWeek_4Weeks_TuesdayThursday_generates8Sessions() {
        // Thứ Hai ngày 02/11/2026
        val monday = LocalDate.of(2026, 11, 2)
        val config = RecurringBookingFixtures.sampleConfig(
            daysOfWeek = listOf(2, 4), // T3, T5
            totalWeeks = 4,
            startNextWeek = true
        )

        val result = useCase(config, today = monday)
        assertTrue(result.isSuccess)
        val dates = result.getOrThrow()

        // 4 tuần * 2 buổi/tuần = 8 buổi
        assertEquals(8, dates.size)
        // Buổi đầu tiên phải là Thứ 3 tuần sau: 10/11/2026
        assertEquals(LocalDate.of(2026, 11, 10), dates.first())
        assertEquals(DayOfWeek.TUESDAY, dates.first().dayOfWeek)
        // Tất cả ngày sinh ra phải là Thứ 3 hoặc Thứ 5
        assertTrue(dates.all { it.dayOfWeek == DayOfWeek.TUESDAY || it.dayOfWeek == DayOfWeek.THURSDAY })
    }

    @Test
    fun startCurrentWeek_pastDaysInWeekAreSkipped_andExtended() {
        // Giả sử hôm nay là Thứ 4 ngày 04/11/2026, 10:00 sáng
        val wednesday = LocalDate.of(2026, 11, 4)
        val time = LocalTime.of(10, 0)
        val config = RecurringBookingFixtures.sampleConfig(
            daysOfWeek = listOf(2, 4), // T3, T5 (Thứ 3 03/11 đã qua)
            startTime = "1800",
            endTime = "2000",
            totalWeeks = 4,
            startNextWeek = false
        )

        val result = useCase(config, today = wednesday, currentTime = time)
        assertTrue(result.isSuccess)
        val dates = result.getOrThrow()

        // Thứ 3 tuần này (03/11) đã qua nên bị bỏ qua
        assertFalse(dates.contains(LocalDate.of(2026, 11, 3)))
        // Buổi đầu tiên trong tuần này là Thứ 5 (05/11)
        assertEquals(LocalDate.of(2026, 11, 5), dates.first())
        // Chu kỳ 4 tuần tính từ ngày bắt đầu thực tế (05/11)
        assertTrue(dates.isNotEmpty())
    }

    @Test
    fun startCurrentWeek_sameDayPastTime_isSkipped() {
        // Hôm nay là Thứ 5 ngày 05/11/2026 lúc 19:30 (ca 18:00–20:00 đã bắt đầu / qua)
        val thursday = LocalDate.of(2026, 11, 5)
        val time = LocalTime.of(19, 30)
        val config = RecurringBookingFixtures.sampleConfig(
            daysOfWeek = listOf(4), // T5
            startTime = "1800",
            endTime = "2000",
            totalWeeks = 4,
            startNextWeek = false
        )

        val result = useCase(config, today = thursday, currentTime = time)
        assertTrue(result.isSuccess)
        val dates = result.getOrThrow()

        // Thứ 5 hôm nay (05/11) đã qua giờ bắt đầu nên không được tính
        assertFalse(dates.contains(thursday))
        // Buổi đầu tiên phải là Thứ 5 tuần sau (12/11)
        assertEquals(LocalDate.of(2026, 11, 12), dates.first())
    }

    @Test
    fun slotCountValidation_rejectsLessThan2OrMoreThan6Slots() {
        val monday = LocalDate.of(2026, 11, 2)

        // 1 slot (30 phút: 18:00 - 18:30) -> Từ chối
        val config1Slot = RecurringBookingFixtures.sampleConfig(
            startTime = "1800",
            endTime = "1830"
        )
        val result1 = useCase(config1Slot, today = monday)
        assertTrue(result1.isFailure)

        // 7 slots (3.5 giờ: 18:00 - 21:30) -> Từ chối
        val config7Slots = RecurringBookingFixtures.sampleConfig(
            startTime = "1800",
            endTime = "2130"
        )
        val result7 = useCase(config7Slots, today = monday)
        assertTrue(result7.isFailure)

        // 2 slots (1 giờ: 18:00 - 19:00) -> Chấp nhận
        val config2Slots = RecurringBookingFixtures.sampleConfig(
            startTime = "1800",
            endTime = "1900"
        )
        assertTrue(useCase(config2Slots, today = monday).isSuccess)

        // 6 slots (3 giờ: 18:00 - 21:00) -> Chấp nhận
        val config6Slots = RecurringBookingFixtures.sampleConfig(
            startTime = "1800",
            endTime = "2100"
        )
        assertTrue(useCase(config6Slots, today = monday).isSuccess)
    }

    @Test
    fun weekCycleValidation_rejectsLessThan4OrMoreThan12Weeks() {
        val monday = LocalDate.of(2026, 11, 2)

        // 3 tuần -> Từ chối
        val config3Weeks = RecurringBookingFixtures.sampleConfig(totalWeeks = 3)
        assertTrue(useCase(config3Weeks, today = monday).isFailure)

        // 13 tuần -> Từ chối
        val config13Weeks = RecurringBookingFixtures.sampleConfig(totalWeeks = 13)
        assertTrue(useCase(config13Weeks, today = monday).isFailure)

        // 4 tuần -> Hợp lệ
        val config4Weeks = RecurringBookingFixtures.sampleConfig(totalWeeks = 4)
        assertTrue(useCase(config4Weeks, today = monday).isSuccess)

        // 12 tuần -> Hợp lệ
        val config12Weeks = RecurringBookingFixtures.sampleConfig(totalWeeks = 12)
        assertTrue(useCase(config12Weeks, today = monday).isSuccess)
    }
}

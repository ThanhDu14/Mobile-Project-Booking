package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import com.example.booking_app.feature.booking.domain.usecase.CheckRecurringAvailabilityUseCase
import com.example.booking_app.feature.booking.fixture.RecurringBookingFixtures
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CheckRecurringAvailabilityUseCaseTest {

    private val repository = mockk<RecurringBookingRepository>()
    private lateinit var useCase: CheckRecurringAvailabilityUseCase

    private val courts = RecurringBookingFixtures.sampleCourts()
    private val priceRules = RecurringBookingFixtures.samplePriceRules()
    private val venueId = "venue-01"
    private val preferredCourtId = "court-01" // Sân 1 - PU

    @Before
    fun setUp() {
        useCase = CheckRecurringAvailabilityUseCase(repository)
    }

    @Test
    fun allSlotsAvailableOnPreferredCourt_sessionStatusIsAvailable() = runTest {
        val dates = listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12))
        // Repository trả về không có slot bận nào
        coEvery { repository.getBusySlotsForDates(venueId, any()) } returns Result.success(emptyList())

        val result = useCase(
            venueId = venueId,
            preferredCourtId = preferredCourtId,
            dates = dates,
            startTime = "1800",
            endTime = "2000",
            allCourts = courts,
            priceRules = priceRules
        )

        assertTrue(result.isSuccess)
        val sessions = result.getOrThrow()
        assertEquals(2, sessions.size)
        assertTrue(sessions.all { it.status == RecurringSessionStatus.AVAILABLE })
        assertTrue(sessions.all { it.courtId == preferredCourtId })
    }

    @Test
    fun preferredCourtHasBusySlot_alternateCourtAvailable_suggestsCourtChange() = runTest {
        val date = LocalDate.of(2026, 11, 10)
        // Sân 1 bị bận lúc 18:30 vào ngày này
        val busySlot = RecurringBookingFixtures.sampleBusySlot(
            courtId = "court-01",
            date = "20261110",
            startTime = "1830",
            status = SlotStatus.BOOKED
        )
        coEvery { repository.getBusySlotsForDates(venueId, any()) } returns Result.success(listOf(busySlot))

        val result = useCase(
            venueId = venueId,
            preferredCourtId = preferredCourtId,
            dates = listOf(date),
            startTime = "1800",
            endTime = "2000",
            allCourts = courts,
            priceRules = priceRules
        )

        assertTrue(result.isSuccess)
        val sessions = result.getOrThrow()
        assertEquals(1, sessions.size)
        val session = sessions.first()

        assertEquals(RecurringSessionStatus.SUGGEST_CHANGE_COURT, session.status)
        assertTrue(session.suggestedCourts.isNotEmpty())
        // Sân 2 cũng là "PU" (cùng surfaceType) nên phải được ưu tiên đứng đầu danh sách gợi ý
        assertEquals("court-02", session.suggestedCourts.first().id)
        assertEquals("PU", session.suggestedCourts.first().surfaceType)
    }

    @Test
    fun allCourtsBusy_sessionStatusIsMustSkip() = runTest {
        val date = LocalDate.of(2026, 11, 10)
        // Tất cả các sân 1, 2, 3, 4 đều bị bận ít nhất 1 slot trong khung 18:00 - 20:00
        val busySlots = listOf(
            RecurringBookingFixtures.sampleBusySlot("court-01", "20261110", "1800"),
            RecurringBookingFixtures.sampleBusySlot("court-02", "20261110", "1830"),
            RecurringBookingFixtures.sampleBusySlot("court-03", "20261110", "1900"),
            RecurringBookingFixtures.sampleBusySlot("court-04", "20261110", "1930")
        )
        coEvery { repository.getBusySlotsForDates(venueId, any()) } returns Result.success(busySlots)

        val result = useCase(
            venueId = venueId,
            preferredCourtId = preferredCourtId,
            dates = listOf(date),
            startTime = "1800",
            endTime = "2000",
            allCourts = courts,
            priceRules = priceRules
        )

        assertTrue(result.isSuccess)
        val session = result.getOrThrow().first()
        assertEquals(RecurringSessionStatus.MUST_SKIP, session.status)
        assertTrue(session.suggestedCourts.isEmpty())
    }
}

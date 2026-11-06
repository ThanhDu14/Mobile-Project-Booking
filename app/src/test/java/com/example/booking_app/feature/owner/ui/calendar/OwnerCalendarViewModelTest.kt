package com.example.booking_app.feature.owner.ui.calendar

import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.fixture.OwnerFixtures
import com.example.booking_app.feature.owner.fixture.FakeOwnerCalendarRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerCalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<OwnerVenueRepository>()
    private var calendarRepository = FakeOwnerCalendarRepository()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getOwnedVenues() } returns Result.success(listOf(OwnerFixtures.venue))
        coEvery { repository.getCourts(OwnerFixtures.VENUE_A) } returns Result.success(OwnerFixtures.courts)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsOwnedVenueAndActualSlotRowsForSelectedDay() = runTest {
        val viewModel = OwnerCalendarViewModel(repository, calendarRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OwnerFixtures.VENUE_A, state.selectedVenueId)
        assertEquals(listOf(OwnerFixtures.courts.first()), state.courts)
        assertEquals(setOf(state.selectedDate), state.slotsByDate.keys)
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
    }

    @Test fun weekModeLoadsSevenLocalCalendarDatesAndNextMovesByWeek() = runTest {
        calendarRepository = FakeOwnerCalendarRepository()
        val viewModel = OwnerCalendarViewModel(repository, calendarRepository)
        advanceUntilIdle()

        viewModel.setMode(OwnerCalendarMode.WEEK)
        advanceUntilIdle()
        val weekStart = viewModel.uiState.value.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val expectedWeekDates = (0L..6L).map { weekStart.plusDays(it).toString().replace("-", "") }.toSet()
        val queriedDates = calendarRepository.observedQueries.last().second
        assertEquals(expectedWeekDates, queriedDates.toSet())
        assertEquals(7, queriedDates.distinct().size)

        val oldDate = viewModel.uiState.value.selectedDate
        viewModel.movePeriod(1)
        advanceUntilIdle()
        assertEquals(oldDate.plusWeeks(1), viewModel.uiState.value.selectedDate)
    }

    @Test fun failedSlotReadShowsRetryableErrorInsteadOfEmptyState() = runTest {
        calendarRepository = FakeOwnerCalendarRepository(failure = IllegalStateException("offline"))
        val viewModel = OwnerCalendarViewModel(repository, calendarRepository)
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isEmpty)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(OwnerFixtures.VENUE_A, calendarRepository.observedQueries.single().first)
    }
}

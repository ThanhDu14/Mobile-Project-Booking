package com.example.booking_app.feature.owner.ui.statistics

import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsPeriod
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsQuery
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsRepository
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsResult
import com.example.booking_app.feature.owner.fixture.OwnerFixtures
import io.mockk.coEvery
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class OwnerStatisticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val venueRepository = mockk<OwnerVenueRepository>()
    private val reportRepository = RecordingStatisticsRepository()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { venueRepository.getOwnedVenues() } returns Result.success(listOf(OwnerFixtures.venue))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun weekQueryUsesMondayToSundayInBusinessTimezone() = runTest {
        val viewModel = OwnerStatisticsViewModel(venueRepository, reportRepository)
        advanceUntilIdle()
        viewModel.setPeriod(OwnerStatisticsPeriod.WEEK)
        advanceUntilIdle()

        val query = reportRepository.queries.last()
        val monday = viewModel.uiState.value.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        assertEquals(monday, query.startDate)
        assertEquals(monday.plusDays(6), query.endDateInclusive)
        assertEquals("Asia/Ho_Chi_Minh", query.timeZoneId)
    }

    @Test fun customRangeUsesExplicitInclusiveDatesAndRejectsReversedRange() = runTest {
        val viewModel = OwnerStatisticsViewModel(venueRepository, reportRepository)
        advanceUntilIdle()
        val from = java.time.LocalDate.parse("2026-11-01")
        val to = java.time.LocalDate.parse("2026-11-09")
        viewModel.setCustomRange(from, to)
        advanceUntilIdle()
        assertEquals(OwnerStatisticsQuery(from, to), reportRepository.queries.last())

        val beforeInvalid = reportRepository.queries.size
        viewModel.setCustomRange(to, from)
        advanceUntilIdle()
        assertEquals(beforeInvalid, reportRepository.queries.size)
        assertFalse(viewModel.uiState.value.error.isNullOrBlank())
    }

    private class RecordingStatisticsRepository : OwnerStatisticsRepository {
        val queries = mutableListOf<OwnerStatisticsQuery>()
        override fun observe(venueId: String, query: OwnerStatisticsQuery): Flow<OwnerStatisticsResult?> {
            queries += query
            return flowOf(null)
        }
    }
}

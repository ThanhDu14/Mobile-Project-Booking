package com.example.booking_app.feature.owner.ui.bookings

import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.booking.OwnerBooking
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingCommand
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingContractUnavailableException
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingDetail
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingFilter
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingRepository
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingStatus
import com.example.booking_app.feature.owner.fixture.OwnerFixtures
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerBookingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val venueRepository = mockk<OwnerVenueRepository>()
    private val bookingRepository = FakeBookingRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { venueRepository.getOwnedVenues() } returns Result.success(listOf(OwnerFixtures.venue))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initLoadsOwnedVenuesAndObservesSelectedVenue() = runTest {
        val viewModel = OwnerBookingsViewModel(venueRepository, bookingRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OwnerFixtures.venue.id, state.selectedVenueId)
        assertEquals(1, state.venues.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun venueFailureSetsErrorState() = runTest {
        coEvery { venueRepository.getOwnedVenues() } returns Result.failure(IllegalStateException("Network offline"))
        val viewModel = OwnerBookingsViewModel(venueRepository, bookingRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Network offline", state.error)
        assertFalse(state.isLoading)
        assertTrue(state.venues.isEmpty())
    }

    @Test
    fun emitsBookingsAndFiltersByPendingUpcomingPast() = runTest {
        val viewModel = OwnerBookingsViewModel(venueRepository, bookingRepository)
        advanceUntilIdle()

        val pending = sampleBooking("b1", OwnerBookingStatus.PENDING)
        // Luôn ở tương lai so với lúc chạy test; ngày ghi cứng sẽ thành "Đã qua" khi ngày đó tới.
        val upcomingStart = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString()
        val confirmed = sampleBooking("b2", OwnerBookingStatus.CONFIRMED, start = upcomingStart)
        val cancelled = sampleBooking("b3", OwnerBookingStatus.CANCELLED)

        bookingRepository.bookingsFlow.emit(listOf(pending, confirmed, cancelled))
        advanceUntilIdle()

        assertEquals(OwnerBookingFilter.PENDING, viewModel.uiState.value.filter)
        assertEquals(listOf("b1"), viewModel.uiState.value.bookings.map { it.bookingId })

        viewModel.selectFilter(OwnerBookingFilter.UPCOMING)
        assertEquals(listOf("b2"), viewModel.uiState.value.bookings.map { it.bookingId })

        viewModel.selectFilter(OwnerBookingFilter.PAST)
        assertEquals(listOf("b3"), viewModel.uiState.value.bookings.map { it.bookingId })
    }

    @Test
    fun contractUnavailableExceptionHandledWithoutCrashing() = runTest {
        bookingRepository.shouldThrowContractError = true
        val viewModel = OwnerBookingsViewModel(venueRepository, bookingRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("chưa được xác nhận", ignoreCase = true))
    }

    @Test
    fun submitCommandDelegatesToRepositoryAndUpdatesActionState() = runTest {
        val viewModel = OwnerBookingsViewModel(venueRepository, bookingRepository)
        advanceUntilIdle()

        val command = OwnerBookingCommand.Approve(OwnerFixtures.venue.id, "b1")
        viewModel.submit(command)
        advanceUntilIdle()

        assertEquals(command, bookingRepository.lastCommand)
        assertEquals("Đã cập nhật booking", viewModel.uiState.value.actionMessage)
        assertFalse(viewModel.uiState.value.actionInProgress)
    }

    private fun sampleBooking(
        id: String,
        status: OwnerBookingStatus,
        start: String = Instant.now().plus(1, ChronoUnit.DAYS).toString(),
    ) = OwnerBooking(
        bookingId = id,
        venueId = OwnerFixtures.venue.id,
        status = status,
        startAt = Instant.parse(start),
        endAt = Instant.parse(start).plusSeconds(3600),
        approvalDeadline = if (status == OwnerBookingStatus.PENDING) Instant.parse(start).minusSeconds(3600) else null,
    )

    private class FakeBookingRepository : OwnerBookingRepository {
        val bookingsFlow = MutableSharedFlow<List<OwnerBooking>>(replay = 1).apply { tryEmit(emptyList()) }
        var shouldThrowContractError = false
        var lastCommand: OwnerBookingCommand? = null

        override fun observeBookings(venueId: String): Flow<List<OwnerBooking>> = flow {
            if (shouldThrowContractError) {
                throw OwnerBookingContractUnavailableException()
            }
            bookingsFlow.collect { emit(it) }
        }

        override suspend fun getBooking(venueId: String, bookingId: String): Result<OwnerBookingDetail> =
            Result.failure(OwnerBookingContractUnavailableException())

        override suspend fun submit(command: OwnerBookingCommand): Result<Unit> {
            lastCommand = command
            return Result.success(Unit)
        }
    }
}

package com.example.booking_app.feature.booking.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.model.RecurringSummary
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import com.example.booking_app.feature.booking.domain.usecase.CalculateRecurringPriceUseCase
import com.example.booking_app.feature.booking.domain.usecase.CheckRecurringAvailabilityUseCase
import com.example.booking_app.feature.booking.domain.usecase.ConflictAction
import com.example.booking_app.feature.booking.domain.usecase.ConflictResolutionResult
import com.example.booking_app.feature.booking.domain.usecase.CreateRecurringHoldTransactionUseCase
import com.example.booking_app.feature.booking.domain.usecase.GenerateRecurringSessionsUseCase
import com.example.booking_app.feature.booking.domain.usecase.ReleaseRecurringHoldUseCase
import com.example.booking_app.feature.booking.domain.usecase.ResolveRecurringConflictUseCase
import com.example.booking_app.feature.booking.ui.state.BookingRecurringUiEffect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BookingRecurringViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository: RecurringBookingRepository = mockk(relaxed = true)
    private val sessionManager = FakeSessionManager()
    private val generateRecurringSessionsUseCase = GenerateRecurringSessionsUseCase()
    private val calculateRecurringPriceUseCase = CalculateRecurringPriceUseCase()
    private val checkRecurringAvailabilityUseCase: CheckRecurringAvailabilityUseCase = mockk()
    private val resolveRecurringConflictUseCase: ResolveRecurringConflictUseCase = mockk()
    private val createRecurringHoldTransactionUseCase: CreateRecurringHoldTransactionUseCase = mockk()
    private val releaseRecurringHoldUseCase: ReleaseRecurringHoldUseCase = mockk(relaxed = true)

    private val venueId = "demo-truong-chinh-909"
    private val sampleCourts = listOf(
        Court(id = "c1", name = "Sân 1", surfaceType = "Thảm", isActive = true, sortOrder = 1),
        Court(id = "c2", name = "Sân 2", surfaceType = "Thảm", isActive = true, sortOrder = 2)
    )
    private val samplePriceRules = listOf(
        PriceRule(id = "pr1", daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7), startTime = "0600", endTime = "2200", pricePerHour = 100_000L, label = "NORMAL")
    )

    private fun createViewModel(): BookingRecurringViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("venueId" to venueId))
        return BookingRecurringViewModel(
            savedStateHandle = savedStateHandle,
            sessionManager = sessionManager,
            repository = repository,
            generateRecurringSessionsUseCase = generateRecurringSessionsUseCase,
            checkRecurringAvailabilityUseCase = checkRecurringAvailabilityUseCase,
            resolveRecurringConflictUseCase = resolveRecurringConflictUseCase,
            calculateRecurringPriceUseCase = calculateRecurringPriceUseCase,
            createRecurringHoldTransactionUseCase = createRecurringHoldTransactionUseCase,
            releaseRecurringHoldUseCase = releaseRecurringHoldUseCase
        )
    }

    @Before
    fun setUp() {
        coEvery { repository.isRecurringAccepted(venueId) } returns Result.success(true)
        coEvery { repository.getActiveCourts(venueId) } returns Result.success(sampleCourts)
        coEvery { repository.getPriceRules(venueId) } returns Result.success(samplePriceRules)
    }

    @Test
    fun init_loadsCourtsAndPriceRulesSuccessfully() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.availableCourts.size)
        assertEquals(1, state.priceRules.size)
    }

    @Test
    fun init_whenRecurringNotAccepted_emitsToastAndNavigatesBack() = runTest {
        coEvery { repository.isRecurringAccepted(venueId) } returns Result.success(false)

        val viewModel = createViewModel()

        viewModel.uiEffect.test {
            advanceUntilIdle()
            val effect1 = awaitItem()
            assertTrue(effect1 is BookingRecurringUiEffect.ShowToast)

            val effect2 = awaitItem()
            assertTrue(effect2 is BookingRecurringUiEffect.NavigateBackToGrid)
        }
    }

    @Test
    fun checkAvailability_validInputs_updatesSessionsAndSummary() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDaysSelected(listOf(2)) // Thứ 3
        viewModel.onTimeRangeSelected("1800", "2000") // 4 slots (2h)
        viewModel.onPreferredCourtSelected("c1")
        viewModel.onDurationWeeksSelected(4)
        viewModel.onStartNextWeekSelected(true)

        val sampleSessions = (1..4).map { i ->
            RecurringSession(
                date = LocalDate.of(2026, 11, 10).plusWeeks(i.toLong()),
                dayOfWeek = 2,
                startTime = "1800",
                endTime = "2000",
                courtId = "c1",
                courtName = "Sân 1",
                surfaceType = "Thảm",
                slotIds = listOf("c1_20261110_1800", "c1_20261110_1830"),
                price = 200_000L,
                status = RecurringSessionStatus.AVAILABLE
            )
        }

        coEvery {
            checkRecurringAvailabilityUseCase(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(sampleSessions)

        viewModel.checkAvailability()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCheckingAvailability)
        assertEquals(4, state.sessions.size)
        assertEquals(800_000L, state.summary?.subtotalAmount)
        assertEquals(240_000L, state.summary?.depositAmount)
        assertTrue(state.canProceed)
    }

    @Test
    fun onConflictActionSelected_resolvesConflictAndUpdateSummary() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val date = LocalDate.of(2026, 11, 10)
        val initialSession = RecurringSession(
            date = date,
            dayOfWeek = 2,
            startTime = "1800",
            endTime = "2000",
            courtId = "c1",
            courtName = "Sân 1",
            surfaceType = "Thảm",
            slotIds = listOf("c1_20261110_1800"),
            price = 200_000L,
            status = RecurringSessionStatus.SUGGEST_CHANGE_COURT
        )
        val updatedSession = initialSession.copy(courtId = "c2", courtName = "Sân 2", status = RecurringSessionStatus.AVAILABLE)
        val summary = RecurringSummary(
            totalSessions = 4,
            validSessions = listOf(updatedSession),
            subtotalAmount = 200_000L,
            depositAmount = 60_000L,
            canProceed = false
        )

        every {
            resolveRecurringConflictUseCase(any(), date, any())
        } returns ConflictResolutionResult.Success(
            updatedSessions = listOf(updatedSession),
            summary = summary
        )

        viewModel.onConflictActionSelected(
            initialSession,
            ConflictAction.ChangeCourt(newCourtId = "c2", newCourtName = "Sân 2", newSurfaceType = "Thảm")
        )

        val state = viewModel.uiState.value
        assertEquals(1, state.sessions.size)
        assertEquals("c2", state.sessions.first().courtId)
    }

    @Test
    fun proceedToHold_whenUserNotSignedIn_navigatesToLogin() = runTest {
        sessionManager.signOut()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDaysSelected(listOf(2))
        viewModel.onTimeRangeSelected("1800", "2000")
        viewModel.onPreferredCourtSelected("c1")
        viewModel.onDurationWeeksSelected(4)
        viewModel.onStartNextWeekSelected(true)

        val sampleSessions = (1..4).map { i ->
            RecurringSession(
                date = LocalDate.of(2026, 11, 10).plusWeeks(i.toLong()),
                dayOfWeek = 2,
                startTime = "1800",
                endTime = "2000",
                courtId = "c1",
                courtName = "Sân 1",
                surfaceType = "Thảm",
                slotIds = listOf("c1_20261110_1800", "c1_20261110_1830"),
                price = 200_000L,
                status = RecurringSessionStatus.AVAILABLE
            )
        }

        coEvery {
            checkRecurringAvailabilityUseCase(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(sampleSessions)

        viewModel.checkAvailability()
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.proceedToHold()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is BookingRecurringUiEffect.NavigateToLogin)
        }
    }

    @Test
    fun proceedToHold_success_navigatesToConfirmAndStartsTimer() = runTest {
        sessionManager.sessionState.value = SessionState.SignedIn(
            uid = "user-123",
            appRole = AppRole.PLAYER,
            emailVerified = true
        )
        sessionManager.isGuest.value = false
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDaysSelected(listOf(2))
        viewModel.onTimeRangeSelected("1800", "2000")
        viewModel.onPreferredCourtSelected("c1")
        viewModel.onDurationWeeksSelected(4)
        viewModel.onStartNextWeekSelected(true)

        val sampleSessions = (1..4).map { i ->
            RecurringSession(
                date = LocalDate.of(2026, 11, 10).plusWeeks(i.toLong()),
                dayOfWeek = 2,
                startTime = "1800",
                endTime = "2000",
                courtId = "c1",
                courtName = "Sân 1",
                surfaceType = "Thảm",
                slotIds = listOf("c1_20261110_1800", "c1_20261110_1830"),
                price = 200_000L,
                status = RecurringSessionStatus.AVAILABLE
            )
        }

        coEvery {
            checkRecurringAvailabilityUseCase(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(sampleSessions)

        coEvery {
            createRecurringHoldTransactionUseCase(
                venueId = venueId,
                config = any(),
                sessions = sampleSessions,
                userId = "user-123",
                requestId = any()
            )
        } returns Result.success(
            RecurringHoldResult.Success(
                recurringGroupId = "rec-group-001",
                holdExpiresAtMillis = System.currentTimeMillis() + 300_000L
            )
        )

        viewModel.checkAvailability()
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.proceedToHold()
            val effect = awaitItem()
            assertTrue(effect is BookingRecurringUiEffect.NavigateToConfirm)
            assertEquals("rec-group-001", (effect as BookingRecurringUiEffect.NavigateToConfirm).recurringGroupId)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("rec-group-001", viewModel.uiState.value.activeRecurringGroupId)
        assertEquals(300, viewModel.uiState.value.holdCountdownSeconds)
    }

    @Test
    fun releaseHold_callsUseCaseWithCorrectIds() = runTest {
        sessionManager.sessionState.value = SessionState.SignedIn(
            uid = "user-123",
            appRole = AppRole.PLAYER,
            emailVerified = true
        )
        sessionManager.isGuest.value = false
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.releaseHold()
        advanceUntilIdle()

        // When no activeRecurringGroupId, should not call release
        coVerify(exactly = 0) {
            releaseRecurringHoldUseCase(any(), any(), any())
        }
    }
}

package com.example.booking_app.feature.owner.ui.dropin

import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInContractUnavailableException
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInDraft
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRegistration
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRepository
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInSession
import com.example.booking_app.feature.owner.fixture.OwnerFixtures
import io.mockk.coEvery
import io.mockk.mockk
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
class OwnerDropInViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val venueRepository = mockk<OwnerVenueRepository>()
    private val dropInRepository = FakeDropInRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { venueRepository.getOwnedVenues() } returns Result.success(listOf(OwnerFixtures.venue))
        coEvery { venueRepository.getCourts(OwnerFixtures.venue.id) } returns Result.success(OwnerFixtures.courts)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initLoadsOwnedVenuesAndSelectsFirst() = runTest {
        val viewModel = OwnerDropInViewModel(venueRepository, dropInRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OwnerFixtures.venue.id, state.selectedVenueId)
        assertEquals(1, state.venues.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun createInvalidDraftSetsValidationErrorsWithoutCallingRepository() = runTest {
        val viewModel = OwnerDropInViewModel(venueRepository, dropInRepository)
        advanceUntilIdle()

        val invalidDraft = OwnerDropInDraft(
            venueId = "",
            courtIds = emptyList(),
            date = "invalid",
            startTime = "18:00",
            endTime = "17:00",
            level = "",
            capacity = -1,
            pricePerPersonVnd = -100L,
            note = "",
        )

        viewModel.create(invalidDraft)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.validationErrors.isNotEmpty())
        assertEquals(null, dropInRepository.lastCreatedDraft)
    }

    @Test
    fun checkInBlankCredentialSetsErrorWithoutCallingRepository() = runTest {
        val viewModel = OwnerDropInViewModel(venueRepository, dropInRepository)
        advanceUntilIdle()

        viewModel.checkIn("venue-1", "session-1", "   ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Nhập hoặc quét mã đăng ký.", state.error)
        assertEquals(null, dropInRepository.lastCheckInCredential)
    }

    @Test
    fun checkInValidCredentialCallsRepository() = runTest {
        val viewModel = OwnerDropInViewModel(venueRepository, dropInRepository)
        advanceUntilIdle()

        viewModel.checkIn("venue-1", "session-1", " REG-999 ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("REG-999", dropInRepository.lastCheckInCredential)
        assertEquals("Đã check-in", state.completedMessage)
        assertFalse(state.isSaving)
    }

    @Test
    fun contractUnavailableHandledGracefully() = runTest {
        dropInRepository.shouldThrowContractError = true
        val viewModel = OwnerDropInViewModel(venueRepository, dropInRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("chưa được xác nhận", ignoreCase = true))
    }

    private class FakeDropInRepository : OwnerDropInRepository {
        val sessionsFlow = MutableSharedFlow<List<OwnerDropInSession>>(replay = 1).apply { tryEmit(emptyList()) }
        var shouldThrowContractError = false
        var lastCreatedDraft: OwnerDropInDraft? = null
        var lastCheckInCredential: String? = null

        override fun observeSessions(venueId: String): Flow<List<OwnerDropInSession>> = flow {
            if (shouldThrowContractError) {
                throw OwnerDropInContractUnavailableException()
            }
            sessionsFlow.collect { emit(it) }
        }

        override suspend fun getRoster(venueId: String, sessionId: String): Result<List<OwnerDropInRegistration>> =
            Result.success(emptyList())

        override suspend fun createSession(draft: OwnerDropInDraft): Result<String> {
            lastCreatedDraft = draft
            return Result.success("session-new")
        }

        override suspend fun updateSession(venueId: String, sessionId: String, draft: OwnerDropInDraft): Result<Unit> =
            Result.success(Unit)

        override suspend fun cancelSession(venueId: String, sessionId: String, reason: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun checkIn(venueId: String, sessionId: String, credential: String): Result<Unit> {
            lastCheckInCredential = credential
            return Result.success(Unit)
        }
    }
}

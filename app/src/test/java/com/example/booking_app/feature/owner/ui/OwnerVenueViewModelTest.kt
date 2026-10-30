package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.PriceRuleInput
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import com.example.booking_app.feature.owner.fixture.OwnerFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerVenueViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeOwnerRepository

    @Before fun setUp() { Dispatchers.setMain(dispatcher); repository = FakeOwnerRepository() }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun listLoadsOwnedVenuesAndExposesEmptyState() = runTest {
        repository.venues = emptyList()
        val vm = OwnerVenueListViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test fun hideRefreshesAfterServerConfirmedResult() = runTest {
        val vm = OwnerVenueListViewModel(repository)
        advanceUntilIdle()
        vm.hide(OwnerFixtures.VENUE_A)
        advanceUntilIdle()
        assertEquals(OwnerFixtures.VENUE_A, repository.hiddenVenueId)
        assertEquals("Đã lưu thay đổi", vm.uiState.value.message)
    }

    @Test fun detailKeepsErrorMessageAfterFailedSave() = runTest {
        repository.updateResult = Result.failure(IllegalStateException("permission denied"))
        val vm = OwnerVenueDetailViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()
        vm.save(OwnerFixtures.editableVenue())
        advanceUntilIdle()
        assertEquals("permission denied", vm.uiState.value.message)
        assertEquals(OwnerFixtures.venue.name, vm.uiState.value.venue?.name)
        assertEquals(OwnerFixtures.editableVenue().name, vm.uiState.value.draft?.name)
    }

    @Test fun listDistinguishesPermissionErrorFromEmpty() = runTest {
        repository.listResult = Result.failure(IllegalStateException("permission denied"))
        val vm = OwnerVenueListViewModel(repository)
        advanceUntilIdle()
        assertEquals("permission denied", vm.uiState.value.error)
        assertTrue(!vm.uiState.value.isEmpty)
        repository.listResult = Result.success(emptyList())
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test fun duplicateVenueSubmitIsIgnoredWhileFirstMutationIsInFlight() = runTest {
        repository.updateGate = CompletableDeferred()
        val vm = OwnerVenueDetailViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()
        vm.save(OwnerFixtures.editableVenue())
        runCurrent()
        vm.save(OwnerFixtures.editableVenue())
        assertEquals(1, repository.updateCalls)
        repository.updateGate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test fun conflictRefreshesAuthoritativeDataAndRetainsDraft() = runTest {
        repository.updateResult = Result.failure(IllegalStateException("stale conflict"))
        val vm = OwnerVenueDetailViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()
        val draft = OwnerFixtures.editableVenue().copy(name = "Bản nháp chưa mất")
        vm.save(draft)
        advanceUntilIdle()
        assertTrue(repository.getVenueCalls >= 2)
        assertEquals("Bản nháp chưa mất", vm.uiState.value.draft?.name)
    }

    @Test fun networkMutationFailureIsNotRetriedAutomatically() = runTest {
        repository.updateResult = Result.failure(IllegalStateException("timeout while saving"))
        val vm = OwnerVenueDetailViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()
        vm.save(OwnerFixtures.editableVenue())
        advanceUntilIdle()
        assertEquals(1, repository.updateCalls)
        assertEquals("timeout while saving", vm.uiState.value.message)
    }

    @Test fun detailReordersAndDeactivatesCourtThroughRepository() = runTest {
        val vm = OwnerVenueDetailViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()
        vm.moveCourt("court-2", -1)
        advanceUntilIdle()
        vm.deactivateCourt("court-1")
        advanceUntilIdle()
        assertTrue(repository.updatedCourtIds.containsAll(listOf("court-1", "court-2")))
        assertEquals("court-1", repository.deactivatedCourtId)
    }

    private class FakeOwnerRepository : OwnerVenueRepository {
        var venues = listOf(OwnerFixtures.venue)
        var listResult: Result<List<OwnerVenue>>? = null
        var hiddenVenueId: String? = null
        var deactivatedCourtId: String? = null
        val updatedCourtIds = mutableListOf<String>()
        var updateResult: Result<Unit> = Result.success(Unit)
        var updateCalls = 0
        var getVenueCalls = 0
        var updateGate: CompletableDeferred<Unit>? = null
        override suspend fun getOwnedVenues() = listResult ?: Result.success(venues)
        override suspend fun getVenue(venueId: String): Result<OwnerVenue> { getVenueCalls++; return Result.success(OwnerFixtures.venue) }
        override suspend fun updateVenue(venueId: String, fields: VenueEditableFields): Result<Unit> {
            updateCalls++
            updateGate?.await()
            return updateResult
        }
        override suspend fun hideVenue(venueId: String): Result<Unit> { hiddenVenueId = venueId; return Result.success(Unit) }
        override suspend fun setAcceptsRecurring(venueId: String, accepts: Boolean) = Result.success(Unit)
        override suspend fun getCourts(venueId: String) = Result.success(OwnerFixtures.courts)
        override suspend fun createCourt(venueId: String, fields: CourtFields) = Result.success("court-new")
        override suspend fun updateCourt(venueId: String, courtId: String, fields: CourtFields): Result<Unit> { updatedCourtIds += courtId; return Result.success(Unit) }
        override suspend fun deactivateCourt(venueId: String, courtId: String): Result<Unit> { deactivatedCourtId = courtId; return Result.success(Unit) }
        override suspend fun getPriceRules(venueId: String): Result<List<OwnerPriceRule>> = Result.success(listOf(OwnerFixtures.priceRule))
        override suspend fun createPriceRule(venueId: String, rule: PriceRuleInput): Result<String> = Result.success("rule-new")
        override suspend fun updatePriceRule(venueId: String, ruleId: String, rule: PriceRuleInput): Result<Unit> = Result.success(Unit)
        override suspend fun deletePriceRule(venueId: String, ruleId: String): Result<Unit> = Result.success(Unit)
        override suspend fun getSlots(venueId: String, date: String) = Result.success(emptyList<OwnerSlot>())
        override suspend fun lockSlots(venueId: String, courtId: String, date: String, slotIds: List<String>, reason: String) = Result.success(Unit)
        override suspend fun unlockSlots(venueId: String, courtId: String, slotIds: List<String>) = Result.success(Unit)
    }
}

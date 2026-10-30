package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.PriceRuleInput
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import com.example.booking_app.feature.owner.fixture.OwnerFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerPriceRuleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePriceRuleRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePriceRuleRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadSetsRulesSortedByDayAndStartTime() = runTest {
        val rule1 = OwnerPriceRule("r1", listOf(4), "1000", "1200", 80_000, "NORMAL")
        val rule2 = OwnerPriceRule("r2", listOf(2), "0600", "0800", 60_000, "NORMAL")
        repository.priceRules = listOf(rule1, rule2)

        val vm = OwnerPriceRuleViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.rules.size)
        assertEquals("r2", state.rules[0].id)
        assertEquals("r1", state.rules[1].id)
        assertTrue(state.isFresh)
    }

    @Test
    fun saveRejectsInvalidPriceAndOverlapWithoutCallingRepository() = runTest {
        val existing = OwnerPriceRule("r1", listOf(1, 2), "0600", "0800", 60_000, "NORMAL")
        repository.priceRules = listOf(existing)

        val vm = OwnerPriceRuleViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()

        // Price not divisible by 2000
        vm.save(null, PriceRuleInput(listOf(3), "0800", "1000", 55_555, "NORMAL"))
        advanceUntilIdle()
        assertEquals(0, repository.createCalls)
        assertNotNull(vm.uiState.value.message)

        // Overlap with existing rule on day 2
        vm.save(null, PriceRuleInput(listOf(2, 3), "0700", "0900", 60_000, "NORMAL"))
        advanceUntilIdle()
        assertEquals(0, repository.createCalls)
        assertNotNull(vm.uiState.value.message)
    }

    @Test
    fun saveNewRuleCallsCreateAndReloads() = runTest {
        val vm = OwnerPriceRuleViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()

        vm.save(null, PriceRuleInput(listOf(3), "0800", "1000", 80_000, "NORMAL"))
        advanceUntilIdle()

        assertEquals(1, repository.createCalls)
        assertEquals("Đã lưu bảng giá", vm.uiState.value.message)
    }

    @Test
    fun saveExistingRuleCallsUpdateAndReloads() = runTest {
        val vm = OwnerPriceRuleViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()

        vm.save("r1", PriceRuleInput(listOf(3), "0800", "1000", 80_000, "NORMAL"))
        advanceUntilIdle()

        assertEquals(1, repository.updateCalls)
        assertEquals("Đã lưu bảng giá", vm.uiState.value.message)
    }

    @Test
    fun requestDeleteTriggersGapWarningWhenDeletionLeavesCoverageGap() = runTest {
        // Venue has openTime="0600", closeTime="2200"
        val rule = OwnerPriceRule("r1", listOf(4), "0600", "0800", 80_000, "NORMAL")
        repository.priceRules = listOf(rule)

        val vm = OwnerPriceRuleViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()

        vm.requestDelete(rule)
        assertEquals("r1", vm.uiState.value.gapWarningRule?.id)

        vm.dismissGapWarning()
        assertNull(vm.uiState.value.gapWarningRule)
    }

    @Test
    fun confirmDeleteDeletesRuleAndReloads() = runTest {
        val rule = OwnerPriceRule("r1", listOf(4), "0600", "0800", 80_000, "NORMAL")
        repository.priceRules = listOf(rule)

        val vm = OwnerPriceRuleViewModel(SavedStateHandle(mapOf("venueId" to OwnerFixtures.VENUE_A)), repository)
        advanceUntilIdle()

        vm.confirmDelete("r1")
        advanceUntilIdle()

        assertEquals("r1", repository.deletedRuleId)
        assertEquals("Đã xóa khung giá", vm.uiState.value.message)
    }

    private class FakePriceRuleRepository : OwnerVenueRepository {
        var priceRules = listOf(OwnerPriceRule("r0", listOf(1), "0600", "1000", 60_000, "NORMAL"))
        var createCalls = 0
        var updateCalls = 0
        var deletedRuleId: String? = null

        override suspend fun getOwnedVenues() = Result.success(listOf(OwnerFixtures.venue))
        override suspend fun getVenue(venueId: String) = Result.success(OwnerFixtures.venue)
        override suspend fun updateVenue(venueId: String, fields: VenueEditableFields) = Result.success(Unit)
        override suspend fun hideVenue(venueId: String) = Result.success(Unit)
        override suspend fun setAcceptsRecurring(venueId: String, accepts: Boolean) = Result.success(Unit)
        override suspend fun getCourts(venueId: String) = Result.success(OwnerFixtures.courts)
        override suspend fun createCourt(venueId: String, fields: CourtFields) = Result.success("c1")
        override suspend fun updateCourt(venueId: String, courtId: String, fields: CourtFields) = Result.success(Unit)
        override suspend fun deactivateCourt(venueId: String, courtId: String) = Result.success(Unit)

        override suspend fun getPriceRules(venueId: String) = Result.success(priceRules)
        override suspend fun createPriceRule(venueId: String, rule: PriceRuleInput): Result<String> {
            createCalls++
            return Result.success("new-rule-id")
        }
        override suspend fun updatePriceRule(venueId: String, ruleId: String, rule: PriceRuleInput): Result<Unit> {
            updateCalls++
            return Result.success(Unit)
        }
        override suspend fun deletePriceRule(venueId: String, ruleId: String): Result<Unit> {
            deletedRuleId = ruleId
            return Result.success(Unit)
        }

        override suspend fun getSlots(venueId: String, date: String) = Result.success(emptyList<OwnerSlot>())
        override suspend fun lockSlots(venueId: String, courtId: String, date: String, slotIds: List<String>, reason: String) = Result.success(Unit)
        override suspend fun unlockSlots(venueId: String, courtId: String, slotIds: List<String>) = Result.success(Unit)
    }
}

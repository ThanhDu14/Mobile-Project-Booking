package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.OwnerWriteGate
import com.example.booking_app.feature.owner.domain.PriceRuleInput
import com.example.booking_app.feature.owner.domain.SlotBlockInput
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class OwnerSlotBlockViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(repo: OwnerVenueRepository? = null) = OwnerSlotBlockViewModel(
        SavedStateHandle(mapOf("venueId" to "venue-1", "courtId" to "court-1")),
        repo
    )

    @Test
    fun previewMapsRangeAndSubmitDoesNotWriteBeforeT007WhenNoRepo() {
        val viewModel = vm(null)
        viewModel.updateForm(SlotBlockInput("court-1", "20261029", "0600", "0730", "EVENT"))
        viewModel.preview()
        assertEquals(3, viewModel.uiState.value.slotIds.size)
        viewModel.submit()
        assertTrue(viewModel.uiState.value.message!!.contains("T007"))
        assertEquals(OwnerWriteGate.SLOT_ATOMIC_SERVER_CONTRACT, viewModel.uiState.value.writeGate)
    }

    @Test
    fun invalidOrStaleFormIsRetainedAndSubmitIsNotEnabledByState() {
        val viewModel = vm(null)
        val input = SlotBlockInput("court-1", "20261029", "0700", "0600", "WALK_IN")
        viewModel.updateForm(input)
        viewModel.submit()
        assertEquals(input, viewModel.uiState.value.form)
        assertTrue(viewModel.uiState.value.error != null)
        viewModel.markStale()
        assertTrue(viewModel.uiState.value.isStale)
    }

    @Test
    fun loadGridMapsSlotsAndIdentifiesRemovableBlocks() = runTest(testDispatcher) {
        val fakeRepo = FakeOwnerVenueRepository(
            initialSlots = listOf(
                OwnerSlot(
                    id = "court-1_20261030_0600",
                    venueId = "venue-1",
                    courtId = "court-1",
                    date = "20261030",
                    startAt = null,
                    endAt = null,
                    status = "BLOCKED",
                    blockReason = "MAINTENANCE",
                    bookingId = null,
                    holdBy = null
                ),
                OwnerSlot(
                    id = "court-1_20261030_0630",
                    venueId = "venue-1",
                    courtId = "court-1",
                    date = "20261030",
                    startAt = null,
                    endAt = null,
                    status = "BLOCKED",
                    blockReason = "DROP_IN", // Protected drop-in
                    bookingId = null,
                    holdBy = null
                ),
                OwnerSlot(
                    id = "court-1_20261030_0700",
                    venueId = "venue-1",
                    courtId = "court-1",
                    date = "20261030",
                    startAt = null,
                    endAt = null,
                    status = "BOOKED",
                    blockReason = null,
                    bookingId = "b1",
                    holdBy = null
                )
            )
        )

        val viewModel = vm(fakeRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val grid = viewModel.uiState.value.gridSlots
        assertTrue(grid.isNotEmpty())

        val maintenanceSlot = grid.first { it.slotId == "court-1_20261030_0600" }
        assertEquals("BLOCKED", maintenanceSlot.status)
        assertTrue(maintenanceSlot.isOwnerRemovable)

        val dropInSlot = grid.first { it.slotId == "court-1_20261030_0630" }
        assertEquals("BLOCKED", dropInSlot.status)
        assertFalse(dropInSlot.isOwnerRemovable) // DROP_IN is not owner-removable

        val bookedSlot = grid.first { it.slotId == "court-1_20261030_0700" }
        assertEquals("BOOKED", bookedSlot.status)
        assertFalse(bookedSlot.isOwnerRemovable)

        val availableSlot = grid.first { it.slotId == "court-1_20261030_0730" }
        assertEquals("AVAILABLE", availableSlot.status)
    }

    @Test
    fun toggleSlotSelectionUpdatesSelectionState() = runTest(testDispatcher) {
        val fakeRepo = FakeOwnerVenueRepository()
        val viewModel = vm(fakeRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val slotId = "court-1_20261030_0800"
        viewModel.toggleSlotSelection(slotId)
        assertTrue(viewModel.uiState.value.selectedSlotIds.contains(slotId))

        viewModel.toggleSlotSelection(slotId)
        assertFalse(viewModel.uiState.value.selectedSlotIds.contains(slotId))
    }

    @Test
    fun lockSelectedSlotsCallsRepositoryAndClearsSelection() = runTest(testDispatcher) {
        val fakeRepo = FakeOwnerVenueRepository()
        val viewModel = vm(fakeRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val slotId = "court-1_20261030_0800"
        viewModel.toggleSlotSelection(slotId)
        viewModel.setReason("EVENT")
        viewModel.lockSelectedSlots()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(slotId), fakeRepo.lastLockedSlotIds)
        assertEquals("EVENT", fakeRepo.lastLockReason)
        assertTrue(viewModel.uiState.value.selectedSlotIds.isEmpty())
        assertTrue(viewModel.uiState.value.message!!.contains("thành công"))
    }

    @Test
    fun unlockSelectedSlotsCallsRepositoryAndClearsSelection() = runTest(testDispatcher) {
        val fakeRepo = FakeOwnerVenueRepository()
        val viewModel = vm(fakeRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val slotId = "court-1_20261030_0830"
        viewModel.toggleSlotSelection(slotId)
        viewModel.unlockSelectedSlots()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(slotId), fakeRepo.lastUnlockedSlotIds)
        assertTrue(viewModel.uiState.value.selectedSlotIds.isEmpty())
        assertTrue(viewModel.uiState.value.message!!.contains("thành công"))
    }

    @Test
    fun lockFailurePreservesSelectionAndDisplaysError() = runTest(testDispatcher) {
        val fakeRepo = FakeOwnerVenueRepository(shouldFailLock = true)
        val viewModel = vm(fakeRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val slotId = "court-1_20261030_0900"
        viewModel.toggleSlotSelection(slotId)
        viewModel.lockSelectedSlots()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.selectedSlotIds.contains(slotId))
        assertTrue(viewModel.uiState.value.error!!.contains("Xung đột"))
    }

    private class FakeOwnerVenueRepository(
        val initialSlots: List<OwnerSlot> = emptyList(),
        val shouldFailLock: Boolean = false
    ) : OwnerVenueRepository {
        var lastLockedSlotIds: List<String> = emptyList()
        var lastLockReason: String = ""
        var lastUnlockedSlotIds: List<String> = emptyList()

        override suspend fun getOwnedVenues(): Result<List<OwnerVenue>> = Result.success(emptyList())
        override suspend fun getVenue(venueId: String): Result<OwnerVenue> = Result.failure(NotImplementedError())
        override suspend fun updateVenue(venueId: String, fields: VenueEditableFields): Result<Unit> = Result.success(Unit)
        override suspend fun hideVenue(venueId: String): Result<Unit> = Result.success(Unit)
        override suspend fun setAcceptsRecurring(venueId: String, accepts: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun getCourts(venueId: String): Result<List<OwnerCourt>> = Result.success(emptyList())
        override suspend fun createCourt(venueId: String, fields: CourtFields): Result<String> = Result.success("c1")
        override suspend fun updateCourt(venueId: String, courtId: String, fields: CourtFields): Result<Unit> = Result.success(Unit)
        override suspend fun deactivateCourt(venueId: String, courtId: String): Result<Unit> = Result.success(Unit)
        override suspend fun getPriceRules(venueId: String): Result<List<OwnerPriceRule>> = Result.success(emptyList())
        override suspend fun createPriceRule(venueId: String, rule: PriceRuleInput): Result<String> = Result.success("r1")
        override suspend fun updatePriceRule(venueId: String, ruleId: String, rule: PriceRuleInput): Result<Unit> = Result.success(Unit)
        override suspend fun deletePriceRule(venueId: String, ruleId: String): Result<Unit> = Result.success(Unit)

        override suspend fun getSlots(venueId: String, date: String): Result<List<OwnerSlot>> =
            Result.success(initialSlots)

        override suspend fun lockSlots(
            venueId: String,
            courtId: String,
            date: String,
            slotIds: List<String>,
            reason: String
        ): Result<Unit> {
            if (shouldFailLock) return Result.failure(RuntimeException("Xung đột: Khung giờ đã bị đặt"))
            lastLockedSlotIds = slotIds
            lastLockReason = reason
            return Result.success(Unit)
        }

        override suspend fun unlockSlots(
            venueId: String,
            courtId: String,
            slotIds: List<String>
        ): Result<Unit> {
            lastUnlockedSlotIds = slotIds
            return Result.success(Unit)
        }
    }
}

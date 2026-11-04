package com.example.booking_app.feature.booking.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.booking.domain.model.BusySlotsUpdate
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotChange
import com.example.booking_app.feature.booking.domain.model.SlotChangeType
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.model.VenueInfo
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import com.example.booking_app.feature.booking.domain.usecase.CalculateTotalPriceUseCase
import com.example.booking_app.feature.booking.domain.usecase.GenerateDailySlotsUseCase
import com.example.booking_app.feature.booking.domain.usecase.HoldSlotsTransactionUseCase
import com.example.booking_app.feature.booking.domain.usecase.ReleaseHoldUseCase
import com.example.booking_app.feature.booking.domain.usecase.ValidateSlotSelectionUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BookingSlotGridViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository: BookingRepository = mockk(relaxed = true)
    private val sessionManager = FakeSessionManager()
    private val validateSlotSelectionUseCase = ValidateSlotSelectionUseCase()
    private val calculateTotalPriceUseCase = CalculateTotalPriceUseCase()
    private val generateDailySlotsUseCase = GenerateDailySlotsUseCase()
    private lateinit var holdSlotsTransactionUseCase: HoldSlotsTransactionUseCase
    private lateinit var releaseHoldUseCase: ReleaseHoldUseCase

    private val venueId = "demo-truong-chinh-909"
    private val venueInfo = VenueInfo(
        id = venueId,
        name = "CLB Cầu Lông Trường Chinh 909",
        address = "909/6 Trường Chinh, Tây Thạnh, Tân Phú",
        districtName = "Tân Phú",
        openTime = "0500",
        closeTime = "2400"
    )

    private val courts = listOf(
        Court(id = "c1", name = "Sân 1", surfaceType = "Thảm", isActive = true, sortOrder = 1),
        Court(id = "c2", name = "Sân 2", surfaceType = "PU", isActive = true, sortOrder = 2)
    )

    private val priceRules = listOf(
        PriceRule(
            id = "r1",
            daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
            startTime = "0500",
            endTime = "1800",
            pricePerHour = 80_000L,
            label = "NORMAL"
        ),
        PriceRule(
            id = "r2",
            daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
            startTime = "1800",
            endTime = "2400",
            pricePerHour = 130_000L,
            label = "PEAK"
        )
    )

    @Before
    fun setUp() {
        holdSlotsTransactionUseCase = HoldSlotsTransactionUseCase(repository)
        releaseHoldUseCase = ReleaseHoldUseCase(repository)

        coEvery { repository.getVenueInfo(venueId) } returns Result.success(venueInfo)
        coEvery { repository.getPriceRules(venueId) } returns Result.success(priceRules)
        coEvery { repository.getActiveCourts(venueId) } returns Result.success(courts)
        coEvery { repository.getBusySlotsRealtime(venueId, any()) } returns flowOf(
            BusySlotsUpdate(allBusySlots = emptyList(), isInitial = true)
        )
    }

    private fun createViewModel(
        handle: SavedStateHandle = SavedStateHandle(mapOf("venueId" to venueId))
    ): BookingSlotGridViewModel {
        return BookingSlotGridViewModel(
            savedStateHandle = handle,
            sessionManager = sessionManager,
            bookingRepository = repository,
            validateSlotSelectionUseCase = validateSlotSelectionUseCase,
            calculateTotalPriceUseCase = calculateTotalPriceUseCase,
            generateDailySlotsUseCase = generateDailySlotsUseCase,
            holdSlotsTransactionUseCase = holdSlotsTransactionUseCase,
            releaseHoldUseCase = releaseHoldUseCase,
            defaultDispatcher = mainRule.dispatcher
        )
    }

    @Test
    fun loadInitialData_loadsVenueInfoAndConstructsMatrixRows() = runTest {
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("CLB Cầu Lông Trường Chinh 909", state.venueName)
        assertEquals("Tân Phú", state.districtName)
        assertFalse(state.isClosed)
        assertEquals(2, state.courts.size)
        assertTrue(state.matrixRows.isNotEmpty())
        // Each row should have 2 slots (for Court 1 and Court 2)
        assertEquals(2, state.matrixRows.first().slots.size)
        // Dynamic peak hour banner computed from price rules (Q5)
        assertNotNull(state.peakHourBannerText)
        assertTrue(state.peakHourBannerText!!.contains("130.000"))
    }

    @Test
    fun emptyVenueId_producesErrorAndClosedState() = runTest {
        val vm = createViewModel(handle = SavedStateHandle())
        val state = vm.uiState.value

        assertTrue(state.isClosed)
        assertTrue(state.errorMessage!!.contains("venueId không hợp lệ"))
    }

    @Test
    fun onSlotClicked_forUnavailableSlots_emitsAppropriateFeedbackWithoutChangingSelection() = runTest {
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        val bookedSlot = TimeSlot("s1", "c1", "20261027", "0600", "0630", 40000L, SlotStatus.BOOKED)
        val heldSlot = TimeSlot("s2", "c1", "20261027", "0630", "0700", 40000L, SlotStatus.HELD)
        val blockedSlot = TimeSlot("s3", "c1", "20261027", "0700", "0730", 40000L, SlotStatus.BLOCKED)
        val pastSlot = TimeSlot("s4", "c1", "20261027", "0730", "0800", 40000L, SlotStatus.PAST)

        vm.uiEffect.test {
            vm.onSlotClicked(bookedSlot)
            val effect1 = awaitItem()
            assertTrue(effect1 is BookingSlotUiEffect.ShowErrorSnackbar)
            assertEquals("Slot này đã được đặt", (effect1 as BookingSlotUiEffect.ShowErrorSnackbar).message)

            vm.onSlotClicked(heldSlot)
            val effect2 = awaitItem()
            assertEquals("Slot này đang được giữ", (effect2 as BookingSlotUiEffect.ShowErrorSnackbar).message)

            vm.onSlotClicked(blockedSlot)
            val effect3 = awaitItem()
            assertEquals("Slot này không khả dụng", (effect3 as BookingSlotUiEffect.ShowErrorSnackbar).message)

            vm.onSlotClicked(pastSlot)
            val effect4 = awaitItem()
            assertEquals("Slot này đã qua giờ", (effect4 as BookingSlotUiEffect.ShowErrorSnackbar).message)

            // Selection remains 0
            assertEquals(0, vm.uiState.value.selectedSlotsCount)
        }
    }

    @Test
    fun onProceedClicked_unauthenticated_savesPendingSelectionAndEmitsNavigateToLogin() = runTest {
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        // Chọn ngày mai để đảm bảo tất cả slot đều AVAILABLE (không bị PAST theo giờ hiện tại)
        vm.selectDate(vm.uiState.value.dates[1])
        testScheduler.advanceUntilIdle()

        // Select 2 continuous slots
        val row1Slot = vm.uiState.value.matrixRows[0].slots[0]
        val row2Slot = vm.uiState.value.matrixRows[1].slots[0]
        vm.onSlotClicked(row1Slot)
        vm.onSlotClicked(row2Slot)
        assertEquals(2, vm.uiState.value.selectedSlotsCount)

        vm.uiEffect.test {
            vm.onProceedClicked()
            val effect = awaitItem()
            assertTrue(effect is BookingSlotUiEffect.NavigateToLogin)
        }
    }

    @Test
    fun onProceedClicked_insufficientSlots_emitsErrorMessage() = runTest {
        // Sign in
        sessionManager.sessionState.value = SessionState.SignedIn(
            uid = "u1",
            appRole = com.example.booking_app.core.session.AppRole.PLAYER,
            emailVerified = true
        )
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        // Chọn ngày mai để đảm bảo tất cả slot đều AVAILABLE
        vm.selectDate(vm.uiState.value.dates[1])
        testScheduler.advanceUntilIdle()

        // Select only 1 slot
        val row1Slot = vm.uiState.value.matrixRows[0].slots[0]
        vm.onSlotClicked(row1Slot)
        assertEquals(1, vm.uiState.value.selectedSlotsCount)

        vm.uiEffect.test {
            vm.onProceedClicked()
            val effect = awaitItem()
            assertTrue(effect is BookingSlotUiEffect.ShowErrorSnackbar)
            assertTrue((effect as BookingSlotUiEffect.ShowErrorSnackbar).message.contains("tối thiểu 2 khung giờ"))
        }
        assertEquals("Sân 1 cần thêm 1 khung giờ liên tiếp mới đặt được (mỗi sân tối thiểu 2 khung, tức 1 giờ).", vm.uiState.value.selectionDetailSummary)
    }

    @Test
    fun onSlotClicked_whenCourtHas8Slots_blocks9thSlotWithCourtSnackbar() = runTest {
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        // Chọn ngày mai để đảm bảo tất cả slot đều AVAILABLE
        vm.selectDate(vm.uiState.value.dates[1])
        testScheduler.advanceUntilIdle()

        // Select 8 slots on Court 1 (slots[0] of each row)
        for (i in 0 until 8) {
            val slot = vm.uiState.value.matrixRows[i].slots[0]
            vm.onSlotClicked(slot)
        }
        assertEquals(8, vm.uiState.value.selectedSlotsCount)

        // Attempt 9th slot on Court 1
        val slot9Court1 = vm.uiState.value.matrixRows[8].slots[0]
        vm.uiEffect.test {
            vm.onSlotClicked(slot9Court1)
            val effect = awaitItem()
            assertTrue(effect is BookingSlotUiEffect.ShowErrorSnackbar)
            assertEquals("Sân 1 đã đủ 8 khung (4 giờ)", (effect as BookingSlotUiEffect.ShowErrorSnackbar).message)
        }
        assertEquals(8, vm.uiState.value.selectedSlotsCount)

        // Attempt 1st slot on Court 2 (slots[1]) -> allowed!
        val slot1Court2 = vm.uiState.value.matrixRows[0].slots[1]
        vm.onSlotClicked(slot1Court2)
        assertEquals(9, vm.uiState.value.selectedSlotsCount)
    }

    @Test
    fun onProceedClicked_holdConflict_removesOnlyConflictedSlots() = runTest {
        sessionManager.sessionState.value = SessionState.SignedIn(
            uid = "u1",
            appRole = com.example.booking_app.core.session.AppRole.PLAYER,
            emailVerified = true
        )
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        // Chọn ngày mai để đảm bảo tất cả slot đều AVAILABLE
        vm.selectDate(vm.uiState.value.dates[1])
        testScheduler.advanceUntilIdle()

        val row1Slot = vm.uiState.value.matrixRows[0].slots[0]
        val row2Slot = vm.uiState.value.matrixRows[1].slots[0]
        val row3Slot = vm.uiState.value.matrixRows[2].slots[0]
        vm.onSlotClicked(row1Slot)
        vm.onSlotClicked(row2Slot)
        vm.onSlotClicked(row3Slot)
        assertEquals(3, vm.uiState.value.selectedSlotsCount)

        // Conflict only on row1Slot
        coEvery {
            repository.holdSlotsAtomic(any(), any(), any(), any(), any(), any())
        } returns Result.success(HoldResult.Conflict(conflictedSlotIds = listOf(row1Slot.slotId)))

        vm.uiEffect.test {
            vm.onProceedClicked()
            testScheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is BookingSlotUiEffect.ShowConflictWarning)
            // Q8: Row 2 and Row 3 remain selected!
            assertEquals(2, vm.uiState.value.selectedSlotsCount)
            assertFalse(vm.uiState.value.selectedSlotIds.contains(row1Slot.slotId))
            assertTrue(vm.uiState.value.selectedSlotIds.contains(row2Slot.slotId))
            assertTrue(vm.uiState.value.selectedSlotIds.contains(row3Slot.slotId))
        }
    }

    @Test
    fun onProceedClicked_singleSlotGap_emitsShowGapResolutionDialog() = runTest {
        sessionManager.sessionState.value = SessionState.SignedIn(
            uid = "u1",
            appRole = com.example.booking_app.core.session.AppRole.PLAYER,
            emailVerified = true
        )
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        vm.selectDate(vm.uiState.value.dates[1])
        testScheduler.advanceUntilIdle()

        val slot0 = vm.uiState.value.matrixRows[0].slots[0]
        val slot1 = vm.uiState.value.matrixRows[1].slots[0]
        val slot3 = vm.uiState.value.matrixRows[3].slots[0]
        val slot4 = vm.uiState.value.matrixRows[4].slots[0]

        vm.onSlotClicked(slot0)
        vm.onSlotClicked(slot1)
        vm.onSlotClicked(slot3)
        vm.onSlotClicked(slot4)
        assertEquals(4, vm.uiState.value.selectedSlotsCount)

        vm.uiEffect.test {
            vm.onProceedClicked()
            val effect = awaitItem()
            assertTrue(effect is BookingSlotUiEffect.ShowGapResolutionDialog)
            val dialogEffect = effect as BookingSlotUiEffect.ShowGapResolutionDialog
            assertEquals("Sân 1", dialogEffect.courtName)
            assertTrue(dialogEffect.canAddSlot)
            assertNotNull(dialogEffect.gapSlotToAdd)
            assertEquals(2, dialogEffect.trailingBlockSlotsToRemove.size)
        }
    }

    @Test
    fun p1_2_matrixBuiltOnDefaultDispatcher_hasExpectedRowsAndSlots() = runTest {
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(38, state.matrixRows.size) // 05:00 to 24:00 = 19 hours * 2 = 38 slots
        assertEquals(2, state.matrixRows[0].slots.size)
        assertEquals("0500", state.matrixRows[0].timeRaw)
        assertEquals("2330", state.matrixRows.last().timeRaw)
    }

    @Test
    fun p1_3_raceCondition_snapshotArrivesBeforeMetadata_matrixBuiltSuccessfully() = runTest {
        val busyFlow = MutableSharedFlow<BusySlotsUpdate>(replay = 1)
        val selectedDateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val bookedSlot = TimeSlot("c1_${selectedDateStr}_0600", "c1", selectedDateStr, "0600", "0630", 40000L, SlotStatus.BOOKED)

        // Snapshot arrives BEFORE metadata completes
        busyFlow.emit(BusySlotsUpdate(allBusySlots = listOf(bookedSlot), isInitial = true))
        coEvery { repository.getBusySlotsRealtime(venueId, any()) } returns busyFlow

        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.matrixRows.isNotEmpty())
        val slot = state.matrixRows.flatMap { it.slots }.find { it.slotId == "c1_${selectedDateStr}_0600" }
        assertNotNull(slot)
        assertEquals(SlotStatus.BOOKED, slot!!.status)
    }

    @Test
    fun p1_3_optimisticRender_showsAvailableSlotsBeforeSnapshotArrives() = runTest {
        val busyFlow = MutableSharedFlow<BusySlotsUpdate>()
        coEvery { repository.getBusySlotsRealtime(venueId, any()) } returns busyFlow

        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        // Optimistic render: metadata completed, busyFlow hasn't emitted yet -> all slots AVAILABLE
        val optimisticState = vm.uiState.value
        assertFalse(optimisticState.isLoading)
        assertTrue(optimisticState.matrixRows.isNotEmpty())
        val selectedDateStr = optimisticState.selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val slot = optimisticState.matrixRows.flatMap { it.slots }.find { it.slotId == "c1_${selectedDateStr}_0600" }
        assertNotNull(slot)
        // With optimistic rendering, slot is available with correct price
        assertEquals(40000L, slot!!.price)

        // Snapshot arrives later with busy slot
        val bookedSlot = TimeSlot("c1_${selectedDateStr}_0600", "c1", selectedDateStr, "0600", "0630", 40000L, SlotStatus.BOOKED)
        busyFlow.emit(BusySlotsUpdate(allBusySlots = listOf(bookedSlot), isInitial = true))
        testScheduler.advanceUntilIdle()

        val updatedState = vm.uiState.value
        val updatedSlot = updatedState.matrixRows.flatMap { it.slots }.find { it.slotId == "c1_${selectedDateStr}_0600" }
        assertEquals(SlotStatus.BOOKED, updatedSlot!!.status)
    }

    @Test
    fun p1_3_selectDate_rendersOptimisticMatrixImmediately() = runTest {
        val busyFlow = MutableSharedFlow<BusySlotsUpdate>()
        coEvery { repository.getBusySlotsRealtime(venueId, any()) } returns busyFlow

        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        val targetDate = vm.uiState.value.dates[3]
        val targetDateStr = targetDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))

        // Select new date
        vm.selectDate(targetDate)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(targetDate, state.selectedDate)
        assertTrue(state.matrixRows.isNotEmpty())
        // All slots for targetDate rendered optimistically
        assertTrue(state.matrixRows.first().slots.all { it.date == targetDateStr })
    }

    @Test
    fun p1_1_applyDelta_addedModifiedRemoved() = runTest {
        val busyFlow = MutableSharedFlow<BusySlotsUpdate>(replay = 1)
        coEvery { repository.getBusySlotsRealtime(venueId, any()) } returns busyFlow

        val vm = createViewModel()
        // Initialize with empty busy slots
        busyFlow.emit(BusySlotsUpdate(allBusySlots = emptyList(), isInitial = true))
        testScheduler.advanceUntilIdle()

        // Chọn ngày mai để tránh PAST
        val tomorrow = vm.uiState.value.dates[1]
        vm.selectDate(tomorrow)
        testScheduler.advanceUntilIdle()

        // 1. ADDED / MODIFIED: Slot becomes HELD
        val dateStr = tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val heldSlot = TimeSlot("c1_${dateStr}_0700", "c1", dateStr, "0700", "0730", 40000L, SlotStatus.HELD, holdExpiresAtMillis = System.currentTimeMillis() + 300_000L)
        busyFlow.emit(
            BusySlotsUpdate(
                allBusySlots = listOf(heldSlot),
                isInitial = false,
                changes = listOf(SlotChange(type = SlotChangeType.ADDED, slot = heldSlot))
            )
        )
        testScheduler.advanceUntilIdle()

        var slot = vm.uiState.value.matrixRows.flatMap { it.slots }.find { it.slotId == heldSlot.slotId }
        assertEquals(SlotStatus.HELD, slot?.status)

        // 2. REMOVED: Hold released -> slot reverts to AVAILABLE
        busyFlow.emit(
            BusySlotsUpdate(
                allBusySlots = emptyList(),
                isInitial = false,
                changes = listOf(SlotChange(type = SlotChangeType.REMOVED, slot = heldSlot))
            )
        )
        testScheduler.advanceUntilIdle()

        slot = vm.uiState.value.matrixRows.flatMap { it.slots }.find { it.slotId == heldSlot.slotId }
        assertEquals(SlotStatus.AVAILABLE, slot?.status)
    }

    @Test
    fun p1_1_applyDelta_realtimeConflict_removesSelectedSlotAndWarns() = runTest {
        val busyFlow = MutableSharedFlow<BusySlotsUpdate>(replay = 1)
        coEvery { repository.getBusySlotsRealtime(venueId, any()) } returns busyFlow

        val vm = createViewModel()
        busyFlow.emit(BusySlotsUpdate(allBusySlots = emptyList(), isInitial = true))
        testScheduler.advanceUntilIdle()

        // Chọn ngày mai để tránh PAST
        val tomorrow = vm.uiState.value.dates[1]
        vm.selectDate(tomorrow)
        testScheduler.advanceUntilIdle()

        val row1Slot = vm.uiState.value.matrixRows[0].slots[0]
        val row2Slot = vm.uiState.value.matrixRows[1].slots[0]
        vm.onSlotClicked(row1Slot)
        vm.onSlotClicked(row2Slot)
        assertEquals(2, vm.uiState.value.selectedSlotsCount)

        // Delta arrives: row1Slot is booked by someone else
        val bookedConflictSlot = row1Slot.copy(status = SlotStatus.BOOKED)
        vm.uiEffect.test {
            busyFlow.emit(
                BusySlotsUpdate(
                    allBusySlots = listOf(bookedConflictSlot),
                    isInitial = false,
                    changes = listOf(SlotChange(type = SlotChangeType.MODIFIED, slot = bookedConflictSlot))
                )
            )
            testScheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is BookingSlotUiEffect.ShowConflictWarning)
            // row1Slot was removed from selection, row2Slot remains
            assertEquals(1, vm.uiState.value.selectedSlotsCount)
            assertFalse(vm.uiState.value.selectedSlotIds.contains(row1Slot.slotId))
            assertTrue(vm.uiState.value.selectedSlotIds.contains(row2Slot.slotId))
        }
    }
}

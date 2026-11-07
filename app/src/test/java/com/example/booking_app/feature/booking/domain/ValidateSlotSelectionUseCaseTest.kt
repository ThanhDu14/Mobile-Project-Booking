package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.SlotSelectionState
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.usecase.ValidateSlotSelectionUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateSlotSelectionUseCaseTest {

    private lateinit var useCase: ValidateSlotSelectionUseCase

    private val courtId = "court-01"
    private val date = "20261027"

    private fun createSlot(startTime: String, endTime: String, status: SlotStatus = SlotStatus.AVAILABLE) =
        TimeSlot(
            slotId = "${courtId}_${date}_$startTime",
            courtId = courtId,
            date = date,
            startTime = startTime,
            endTime = endTime,
            price = 40_000L,
            status = status
        )

    private val slot0600 = createSlot("0600", "0630")
    private val slot0630 = createSlot("0630", "0700")
    private val slot0700 = createSlot("0700", "0730")
    private val slot0730 = createSlot("0730", "0800")
    private val slot0800 = createSlot("0800", "0830")
    private val slot0830 = createSlot("0830", "0900")
    private val slot0900 = createSlot("0900", "0930")
    private val slot0930 = createSlot("0930", "1000")
    private val slot1000 = createSlot("1000", "1030")

    private val allSlots = listOf(
        slot0600, slot0630, slot0700, slot0730,
        slot0800, slot0830, slot0900, slot0930, slot1000
    )

    @Before
    fun setUp() {
        useCase = ValidateSlotSelectionUseCase()
    }

    @Test
    fun selectFirstAvailableSlot_becomesSelected() {
        val initial = SlotSelectionState()
        val result = useCase(initial, slot0600, allSlots)

        assertEquals(1, result.selectedSlots.size)
        assertEquals(slot0600.copy(status = SlotStatus.SELECTED), result.selectedSlots.first())
        assertFalse(result.isValid) // Needs at least 2 slots to proceed
    }

    @Test
    fun selectAdjacentSlot_extendsContinuousChain() {
        val state1 = useCase(SlotSelectionState(), slot0600, allSlots)
        val state2 = useCase(state1, slot0630, allSlots)

        assertEquals(2, state2.selectedSlots.size)
        assertEquals(listOf(slot0600.slotId, slot0630.slotId), state2.selectedSlots.map { it.slotId })
        assertTrue(state2.isValid)
    }

    @Test
    fun selectNonAdjacentSlot_resetsChainAndStartsNewSelection() {
        val state1 = useCase(SlotSelectionState(), slot0600, allSlots)
        val state2 = useCase(state1, slot0630, allSlots)
        assertEquals(2, state2.selectedSlots.size)

        // Click non-adjacent slot 0800
        val state3 = useCase(state2, slot0800, allSlots)
        assertEquals(1, state3.selectedSlots.size)
        assertEquals(slot0800.slotId, state3.selectedSlots.first().slotId)
    }

    @Test
    fun deselectHeadOfChain_removesHeadAndKeepsRemainderContinuous() {
        var state = SlotSelectionState()
        state = useCase(state, slot0600, allSlots)
        state = useCase(state, slot0630, allSlots)
        state = useCase(state, slot0700, allSlots)
        assertEquals(3, state.selectedSlots.size)

        // Deselect head (0600)
        val afterDeselect = useCase(state, slot0600, allSlots)
        assertEquals(2, afterDeselect.selectedSlots.size)
        assertEquals(listOf(slot0630.slotId, slot0700.slotId), afterDeselect.selectedSlots.map { it.slotId })
    }

    @Test
    fun deselectTailOfChain_removesTailAndKeepsRemainderContinuous() {
        var state = SlotSelectionState()
        state = useCase(state, slot0600, allSlots)
        state = useCase(state, slot0630, allSlots)
        state = useCase(state, slot0700, allSlots)
        assertEquals(3, state.selectedSlots.size)

        // Deselect tail (0700)
        val afterDeselect = useCase(state, slot0700, allSlots)
        assertEquals(2, afterDeselect.selectedSlots.size)
        assertEquals(listOf(slot0600.slotId, slot0630.slotId), afterDeselect.selectedSlots.map { it.slotId })
    }

    @Test
    fun deselectMiddleOfChain_truncatesFromMiddleToEnd() {
        var state = SlotSelectionState()
        state = useCase(state, slot0600, allSlots)
        state = useCase(state, slot0630, allSlots)
        state = useCase(state, slot0700, allSlots)
        state = useCase(state, slot0730, allSlots)
        assertEquals(4, state.selectedSlots.size)

        // Deselect middle slot (0630) -> cuts off 0630, 0700, 0730
        val afterDeselect = useCase(state, slot0630, allSlots)
        assertEquals(1, afterDeselect.selectedSlots.size)
        assertEquals(listOf(slot0600.slotId), afterDeselect.selectedSlots.map { it.slotId })
    }

    @Test
    fun selectUnavailableSlot_isBlocked() {
        val bookedSlot = createSlot("0600", "0630", status = SlotStatus.BOOKED)
        val heldSlot = createSlot("0630", "0700", status = SlotStatus.HELD)
        val blockedSlot = createSlot("0700", "0730", status = SlotStatus.BLOCKED)
        val pastSlot = createSlot("0730", "0800", status = SlotStatus.PAST)

        val state = SlotSelectionState()
        assertEquals(0, useCase(state, bookedSlot, allSlots).selectedSlots.size)
        assertEquals(0, useCase(state, heldSlot, allSlots).selectedSlots.size)
        assertEquals(0, useCase(state, blockedSlot, allSlots).selectedSlots.size)
        assertEquals(0, useCase(state, pastSlot, allSlots).selectedSlots.size)
    }

    @Test
    fun selectExceedingMax8Slots_isBlocked() {
        var state = SlotSelectionState()
        val slots = listOf(
            slot0600, slot0630, slot0700, slot0730,
            slot0800, slot0830, slot0900, slot0930
        )
        // Select 8 continuous slots (4 hours)
        for (slot in slots) {
            state = useCase(state, slot, allSlots)
        }
        assertEquals(8, state.selectedSlots.size)

        // Try selecting 9th slot (1000) -> should be blocked
        val state9 = useCase(state, slot1000, allSlots)
        assertEquals(8, state9.selectedSlots.size)
        assertEquals("Tối đa 8 khung giờ (4 tiếng) cho một lần đặt", state9.errorMessage)
    }

    @Test
    fun validateForProceed_requiresMinimum2Slots() {
        val singleSlotState = SlotSelectionState(
            selectedSlots = listOf(slot0600),
            totalPrice = 40_000L,
            depositAmount = 12_000L,
            isValid = false
        )
        val result1 = useCase.validateForProceed(singleSlotState)
        assertTrue(result1.isFailure)

        val twoSlotsState = SlotSelectionState(
            selectedSlots = listOf(slot0600, slot0630),
            totalPrice = 80_000L,
            depositAmount = 24_000L,
            isValid = true
        )
        val result2 = useCase.validateForProceed(twoSlotsState)
        assertTrue(result2.isSuccess)
    }
}

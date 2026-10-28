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
    fun selectExceedingMax8SlotsPerCourt_isBlockedOnSameCourt_allowedOnOtherCourt() {
        var state = SlotSelectionState()
        val slotsCourt1 = listOf(
            slot0600, slot0630, slot0700, slot0730,
            slot0800, slot0830, slot0900, slot0930
        )
        // Select 8 continuous slots on Court 1 (4 hours)
        for (slot in slotsCourt1) {
            state = useCase(state, slot, allSlots)
        }
        assertEquals(8, state.selectedSlots.size)

        // Try selecting 9th slot (1000) on Court 1 -> should be blocked
        val state9 = useCase(state, slot1000, allSlots)
        assertEquals(8, state9.selectedSlots.size)
        assertEquals("Sân đã đủ 8 khung (4 giờ)", state9.errorMessage)

        // Selecting 1st slot on Court 2 -> ALLOWED (cap 8 per court)
        val court2Id = "court-02"
        val court2Slot = slot0600.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0600")
        val stateWithCourt2 = useCase(state, court2Slot, allSlots + listOf(court2Slot))
        assertEquals(9, stateWithCourt2.selectedSlots.size)
    }

    @Test
    fun multiCourtSelection_allowsIndependentChainsPerCourt() {
        // Q1: Một đơn chứa nhiều chuỗi (nhiều sân, cùng ngày); tổng tối đa 8 slot
        val court2Id = "court-02"
        val court2Slot0600 = slot0600.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0600")
        val court2Slot0630 = slot0630.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0630")
        val allMultiCourtSlots = allSlots + listOf(court2Slot0600, court2Slot0630)

        var state = SlotSelectionState()
        // Chọn 2 slot trên court 1
        state = useCase(state, slot0600, allMultiCourtSlots)
        state = useCase(state, slot0630, allMultiCourtSlots)
        assertEquals(2, state.selectedSlots.size)

        // Chọn thêm 2 slot trên court 2
        state = useCase(state, court2Slot0600, allMultiCourtSlots)
        state = useCase(state, court2Slot0630, allMultiCourtSlots)
        assertEquals(4, state.selectedSlots.size)

        val court1Slots = state.selectedSlots.filter { it.courtId == courtId }
        val court2Slots = state.selectedSlots.filter { it.courtId == court2Id }
        assertEquals(2, court1Slots.size)
        assertEquals(2, court2Slots.size)
        assertTrue(state.isValid)
    }

    @Test
    fun nonAdjacentClickOnOneCourt_resetsOnlyThatCourtAndKeepsOtherCourts() {
        // Q1 & Q9: Bấm không liền kề trên sân A reset sân A, giữ nguyên sân B và báo lỗi
        val court2Id = "court-02"
        val court2Slot0600 = slot0600.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0600")
        val court2Slot0630 = slot0630.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0630")
        val allMultiCourtSlots = allSlots + listOf(court2Slot0600, court2Slot0630)

        var state = SlotSelectionState()
        state = useCase(state, slot0600, allMultiCourtSlots)
        state = useCase(state, slot0630, allMultiCourtSlots)
        state = useCase(state, court2Slot0600, allMultiCourtSlots)
        state = useCase(state, court2Slot0630, allMultiCourtSlots)
        assertEquals(4, state.selectedSlots.size)

        // Bấm slot không liền kề trên Court 1 (0800)
        val afterNonAdjacent = useCase(state, slot0800, allMultiCourtSlots)

        // Court 1 chỉ còn 1 slot mới (0800)
        val court1Slots = afterNonAdjacent.selectedSlots.filter { it.courtId == courtId }
        assertEquals(1, court1Slots.size)
        assertEquals(slot0800.slotId, court1Slots.first().slotId)

        // Court 2 vẫn giữ nguyên 2 slot
        val court2Slots = afterNonAdjacent.selectedSlots.filter { it.courtId == court2Id }
        assertEquals(2, court2Slots.size)
        assertEquals(3, afterNonAdjacent.selectedSlots.size)

        // Báo lỗi Q9
        assertEquals("Vui lòng chọn các khung giờ liên tiếp trên cùng sân", afterNonAdjacent.errorMessage)
    }

    @Test
    fun deselectOnOneCourt_doesNotAffectOtherCourts() {
        val court2Id = "court-02"
        val court2Slot0600 = slot0600.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0600")
        val court2Slot0630 = slot0630.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0630")
        val allMultiCourtSlots = allSlots + listOf(court2Slot0600, court2Slot0630)

        var state = SlotSelectionState()
        state = useCase(state, slot0600, allMultiCourtSlots)
        state = useCase(state, slot0630, allMultiCourtSlots)
        state = useCase(state, court2Slot0600, allMultiCourtSlots)
        state = useCase(state, court2Slot0630, allMultiCourtSlots)
        assertEquals(4, state.selectedSlots.size)

        // Deselect slot0630 trên Court 1
        state = useCase(state, slot0630, allMultiCourtSlots)
        val court1Slots = state.selectedSlots.filter { it.courtId == courtId }
        val court2Slots = state.selectedSlots.filter { it.courtId == court2Id }
        assertEquals(1, court1Slots.size)
        assertEquals(2, court2Slots.size)
        assertEquals(3, state.selectedSlots.size)
    }

    @Test
    fun validateForProceed_requiresMinimum2SlotsPerCourt() {
        val court2Id = "court-02"
        val courtNames = mapOf(courtId to "Sân 1", court2Id to "Sân 2")
        val court2Slot0600 = slot0600.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0600")
        val court2Slot0630 = slot0630.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0630")

        // Case 1: 1 slot Sân 1
        val state1 = SlotSelectionState(selectedSlots = listOf(slot0600))
        assertFalse(useCase.isSelectionValid(state1.selectedSlots))
        val result1 = useCase.validateForProceed(state1, courtNames)
        assertTrue(result1.isFailure)
        assertTrue(result1.exceptionOrNull()?.message?.contains("Sân 1") == true)

        // Case 2: 1 slot Sân 1 + 1 slot Sân 2
        val state2 = SlotSelectionState(selectedSlots = listOf(slot0600, court2Slot0600))
        assertFalse(useCase.isSelectionValid(state2.selectedSlots))
        val result2 = useCase.validateForProceed(state2, courtNames)
        assertTrue(result2.isFailure)

        // Case 3: 2 slot Sân 1 + 1 slot Sân 2
        val state3 = SlotSelectionState(selectedSlots = listOf(slot0600, slot0630, court2Slot0600))
        assertFalse(useCase.isSelectionValid(state3.selectedSlots))
        val result3 = useCase.validateForProceed(state3, courtNames)
        assertTrue(result3.isFailure)
        assertTrue(result3.exceptionOrNull()?.message?.contains("Sân 2") == true)

        // Case 4: 2 slot Sân 1 + 2 slot Sân 2 -> SUCCESS!
        val state4 = SlotSelectionState(selectedSlots = listOf(slot0600, slot0630, court2Slot0600, court2Slot0630))
        assertTrue(useCase.isSelectionValid(state4.selectedSlots))
        val result4 = useCase.validateForProceed(state4, courtNames)
        assertTrue(result4.isSuccess)
    }

    @Test
    fun multiCourtValidation_8Plus8SlotsAllowed() {
        val court2Id = "court-02"
        val slotsCourt1 = listOf(
            slot0600, slot0630, slot0700, slot0730,
            slot0800, slot0830, slot0900, slot0930
        )
        val slotsCourt2 = slotsCourt1.map { it.copy(courtId = court2Id, slotId = "${court2Id}_${date}_${it.startTime}") }

        val all16Slots = slotsCourt1 + slotsCourt2
        assertEquals(16, all16Slots.size)
        assertTrue(useCase.isSelectionValid(all16Slots))
        val result = useCase.validateForProceed(SlotSelectionState(selectedSlots = all16Slots))
        assertTrue(result.isSuccess)
    }

    @Test
    fun multiCourtValidation_total9SlotsAcrossCourtsAllowed() {
        // 2 slots Court 1 + 2 slots Court 2 + 2 slots Court 3 + 3 slots Court 4 = 9 slots total
        val c1Slots = listOf(slot0600, slot0630)
        val c2Slots = listOf(
            slot0600.copy(courtId = "c2", slotId = "c2_${date}_0600"),
            slot0630.copy(courtId = "c2", slotId = "c2_${date}_0630")
        )
        val c3Slots = listOf(
            slot0600.copy(courtId = "c3", slotId = "c3_${date}_0600"),
            slot0630.copy(courtId = "c3", slotId = "c3_${date}_0630")
        )
        val c4Slots = listOf(
            slot0600.copy(courtId = "c4", slotId = "c4_${date}_0600"),
            slot0630.copy(courtId = "c4", slotId = "c4_${date}_0630"),
            slot0700.copy(courtId = "c4", slotId = "c4_${date}_0700")
        )
        val total9Slots = c1Slots + c2Slots + c3Slots + c4Slots
        assertEquals(9, total9Slots.size)
        assertTrue(useCase.isSelectionValid(total9Slots))
        val result = useCase.validateForProceed(SlotSelectionState(selectedSlots = total9Slots))
        assertTrue(result.isSuccess)
    }

    @Test
    fun findIncompleteCourt_returnsHelpfulHintWhenUnder2Slots() {
        val court2Id = "court-02"
        val courtNames = mapOf(courtId to "Sân 1", court2Id to "Sân 2")
        val court2Slot0600 = slot0600.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0600")

        // 2 slots Sân 1 + 1 slot Sân 2
        val slots = listOf(slot0600, slot0630, court2Slot0600)
        val hint = useCase.findIncompleteCourt(slots, courtNames)
        assertEquals("Sân 2 cần thêm 1 khung giờ liên tiếp mới đặt được (mỗi sân tối thiểu 2 khung, tức 1 giờ).", hint)

        // Empty slots -> null
        assertEquals(null, useCase.findIncompleteCourt(emptyList(), courtNames))

        // All courts complete (>= 2 slots) -> null
        val court2Slot0630 = slot0630.copy(courtId = court2Id, slotId = "${court2Id}_${date}_0630")
        assertEquals(null, useCase.findIncompleteCourt(listOf(slot0600, slot0630, court2Slot0600, court2Slot0630), courtNames))
    }
}

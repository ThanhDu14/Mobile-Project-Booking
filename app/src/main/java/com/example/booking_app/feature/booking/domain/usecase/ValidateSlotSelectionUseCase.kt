package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.SlotSelectionState
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import javax.inject.Inject

class ValidateSlotSelectionUseCase @Inject constructor(
    private val calculateTotalPriceUseCase: CalculateTotalPriceUseCase
) {
    // Secondary constructor for testing without DI
    constructor() : this(CalculateTotalPriceUseCase())

    operator fun invoke(
        currentSelection: SlotSelectionState,
        clickedSlot: TimeSlot,
        allSlots: List<TimeSlot>
    ): SlotSelectionState {
        // Only AVAILABLE or already SELECTED slots can be clicked
        val isAlreadySelected = currentSelection.selectedSlots.any { it.slotId == clickedSlot.slotId }
        if (!isAlreadySelected && clickedSlot.status != SlotStatus.AVAILABLE) {
            return currentSelection
        }

        val currentList = currentSelection.selectedSlots

        if (isAlreadySelected) {
            // Deselection flow
            val clickedIndex = currentList.indexOfFirst { it.slotId == clickedSlot.slotId }
            val newList = when (clickedIndex) {
                0 -> currentList.drop(1)
                currentList.size - 1 -> currentList.dropLast(1)
                else -> currentList.subList(0, clickedIndex) // Middle click truncates from middle to end
            }

            return updateStateWithSlots(newList)
        } else {
            // Selection flow
            if (currentList.isEmpty()) {
                val newSlot = clickedSlot.copy(status = SlotStatus.SELECTED)
                return updateStateWithSlots(listOf(newSlot))
            }

            val head = currentList.first()
            val tail = currentList.last()

            val isPrepended = clickedSlot.endTime == head.startTime
            val isAppended = clickedSlot.startTime == tail.endTime

            if (isPrepended || isAppended) {
                // Continuous selection
                if (currentList.size >= MAX_SLOTS) {
                    return currentSelection.copy(
                        errorMessage = "Tối đa $MAX_SLOTS khung giờ (4 tiếng) cho một lần đặt"
                    )
                }

                val selectedSlot = clickedSlot.copy(status = SlotStatus.SELECTED)
                val newList = if (isPrepended) {
                    listOf(selectedSlot) + currentList
                } else {
                    currentList + selectedSlot
                }

                return updateStateWithSlots(newList)
            } else {
                // Non-continuous click -> reset and start new chain at clicked slot
                val newSlot = clickedSlot.copy(status = SlotStatus.SELECTED)
                return updateStateWithSlots(listOf(newSlot))
            }
        }
    }

    fun validateForProceed(state: SlotSelectionState): Result<SlotSelectionState> {
        return if (state.selectedSlots.size < MIN_SLOTS) {
            Result.failure(
                IllegalArgumentException("Vui lòng chọn tối thiểu $MIN_SLOTS khung giờ liên tiếp (1 tiếng)")
            )
        } else {
            Result.success(state.copy(isValid = true))
        }
    }

    private fun updateStateWithSlots(slots: List<TimeSlot>): SlotSelectionState {
        if (slots.isEmpty()) {
            return SlotSelectionState(
                selectedSlots = emptyList(),
                totalPrice = 0L,
                depositAmount = 0L,
                isValid = false,
                errorMessage = null
            )
        }

        val (total, deposit) = calculateTotalPriceUseCase(slots)
        return SlotSelectionState(
            selectedSlots = slots,
            totalPrice = total,
            depositAmount = deposit,
            isValid = slots.size >= MIN_SLOTS,
            errorMessage = null
        )
    }

    companion object {
        const val MIN_SLOTS = 2 // 1 hour
        const val MAX_SLOTS = 8 // 4 hours (FR-026, Decision #1)
    }
}

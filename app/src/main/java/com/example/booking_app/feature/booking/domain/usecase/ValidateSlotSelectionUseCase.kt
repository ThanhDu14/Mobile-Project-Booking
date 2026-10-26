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
        val targetCourtId = clickedSlot.courtId
        val targetCourtSlots = currentList.filter { it.courtId == targetCourtId }
        val otherCourtsSlots = currentList.filter { it.courtId != targetCourtId }

        if (isAlreadySelected) {
            // Deselection flow trên sân mục tiêu (không ảnh hưởng sân khác)
            val clickedIndex = targetCourtSlots.indexOfFirst { it.slotId == clickedSlot.slotId }
            val newCourtSlots = when (clickedIndex) {
                0 -> targetCourtSlots.drop(1)
                targetCourtSlots.size - 1 -> targetCourtSlots.dropLast(1)
                else -> targetCourtSlots.subList(0, clickedIndex) // Cắt từ giữa về cuối dãy trên sân này
            }

            return updateStateWithSlots(otherCourtsSlots + newCourtSlots)
        } else {
            // Selection flow
            if (targetCourtSlots.isEmpty()) {
                if (currentList.size >= MAX_SLOTS) {
                    return currentSelection.copy(
                        errorMessage = "Tối đa $MAX_SLOTS khung giờ (4 tiếng) cho một lần đặt"
                    )
                }
                val newSlot = clickedSlot.copy(status = SlotStatus.SELECTED)
                return updateStateWithSlots(otherCourtsSlots + listOf(newSlot))
            }

            val head = targetCourtSlots.first()
            val tail = targetCourtSlots.last()

            val isPrepended = clickedSlot.endTime == head.startTime
            val isAppended = clickedSlot.startTime == tail.endTime

            if (isPrepended || isAppended) {
                // Continuous selection trên sân mục tiêu
                if (currentList.size >= MAX_SLOTS) {
                    return currentSelection.copy(
                        errorMessage = "Tối đa $MAX_SLOTS khung giờ (4 tiếng) cho một lần đặt"
                    )
                }

                val selectedSlot = clickedSlot.copy(status = SlotStatus.SELECTED)
                val newCourtSlots = if (isPrepended) {
                    listOf(selectedSlot) + targetCourtSlots
                } else {
                    targetCourtSlots + selectedSlot
                }

                return updateStateWithSlots(otherCourtsSlots + newCourtSlots)
            } else {
                // Không liền kề trên cùng sân -> reset chuỗi sân này, giữ nguyên sân khác, thêm nhắc nhở (Q9)
                val newSlot = clickedSlot.copy(status = SlotStatus.SELECTED)
                val updatedState = updateStateWithSlots(otherCourtsSlots + listOf(newSlot))
                return updatedState.copy(
                    errorMessage = "Vui lòng chọn các khung giờ liên tiếp trên cùng sân"
                )
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

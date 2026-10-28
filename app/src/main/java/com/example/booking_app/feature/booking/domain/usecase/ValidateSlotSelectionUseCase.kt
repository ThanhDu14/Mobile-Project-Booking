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
            // Selection flow: Cap tối đa 8 khung tính theo TỪNG SÂN (FR-026)
            if (targetCourtSlots.size >= MAX_SLOTS_PER_COURT) {
                return currentSelection.copy(
                    errorMessage = "Sân đã đủ $MAX_SLOTS_PER_COURT khung (4 giờ)"
                )
            }

            if (targetCourtSlots.isEmpty()) {
                val newSlot = clickedSlot.copy(status = SlotStatus.SELECTED)
                return updateStateWithSlots(otherCourtsSlots + listOf(newSlot))
            }

            val head = targetCourtSlots.first()
            val tail = targetCourtSlots.last()

            val isPrepended = clickedSlot.endTime == head.startTime
            val isAppended = clickedSlot.startTime == tail.endTime

            if (isPrepended || isAppended) {
                // Continuous selection trên sân mục tiêu
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

    /**
     * Kiểm tra điều kiện hợp lệ để giữ chỗ:
     * MỖI sân có trong lựa chọn phải có từ 2 đến 8 khung giờ liên tiếp (1h đến 4h).
     */
    fun validateForProceed(
        state: SlotSelectionState,
        courtNames: Map<String, String> = emptyMap()
    ): Result<SlotSelectionState> {
        if (state.selectedSlots.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Chưa chọn khung giờ nào")
            )
        }

        val courtGroups = state.selectedSlots.groupBy { it.courtId }
        for ((courtId, courtSlots) in courtGroups) {
            val courtName = courtNames[courtId] ?: "Sân"
            if (courtSlots.size < MIN_SLOTS_PER_COURT) {
                return Result.failure(
                    IllegalArgumentException("$courtName mới có ${courtSlots.size} khung giờ. Mỗi sân tối thiểu $MIN_SLOTS_PER_COURT khung giờ liên tiếp (1 giờ)")
                )
            }
            if (courtSlots.size > MAX_SLOTS_PER_COURT) {
                return Result.failure(
                    IllegalArgumentException("$courtName đã vượt quá $MAX_SLOTS_PER_COURT khung giờ (4 giờ)")
                )
            }
            if (!isContinuousChain(courtSlots)) {
                return Result.failure(
                    IllegalArgumentException("Khung giờ trên $courtName không liên tiếp")
                )
            }
        }

        return Result.success(state.copy(isValid = true))
    }

    /**
     * Kiểm tra tính hợp lệ nhanh của danh sách slot theo quy tắc từng sân
     */
    fun isSelectionValid(slots: List<TimeSlot>): Boolean {
        if (slots.isEmpty()) return false
        val courtGroups = slots.groupBy { it.courtId }
        return courtGroups.values.all { courtSlots ->
            courtSlots.size in MIN_SLOTS_PER_COURT..MAX_SLOTS_PER_COURT && isContinuousChain(courtSlots)
        }
    }

    /**
     * Tìm sân đầu tiên chưa đủ điều kiện tối thiểu 2 khung để hiển thị gợi ý trên UI
     */
    fun findIncompleteCourt(
        slots: List<TimeSlot>,
        courtNames: Map<String, String> = emptyMap()
    ): String? {
        if (slots.isEmpty()) return null
        val courtGroups = slots.groupBy { it.courtId }
        for ((courtId, courtSlots) in courtGroups) {
            if (courtSlots.size < MIN_SLOTS_PER_COURT) {
                val name = courtNames[courtId] ?: "Sân"
                return "$name cần thêm ${MIN_SLOTS_PER_COURT - courtSlots.size} khung giờ liên tiếp mới đặt được (mỗi sân tối thiểu 2 khung, tức 1 giờ)."
            }
        }
        return null
    }

    private fun isContinuousChain(courtSlots: List<TimeSlot>): Boolean {
        if (courtSlots.size <= 1) return true
        val sorted = courtSlots.sortedBy { it.startTime }
        for (i in 0 until sorted.size - 1) {
            if (sorted[i].endTime != sorted[i + 1].startTime) {
                return false
            }
        }
        return true
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
            isValid = isSelectionValid(slots),
            errorMessage = null
        )
    }

    companion object {
        const val MIN_SLOTS_PER_COURT = 2 // 1 hour (Mỗi sân tối thiểu 2 khung)
        const val MAX_SLOTS_PER_COURT = 8 // 4 hours (Mỗi sân tối đa 8 khung theo FR-026, Cap theo từng sân)

        // Tương thích ngược
        const val MIN_SLOTS = MIN_SLOTS_PER_COURT
        const val MAX_SLOTS = MAX_SLOTS_PER_COURT
    }
}

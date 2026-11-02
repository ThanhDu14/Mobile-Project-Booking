package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.SlotSelectionState
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import javax.inject.Inject

data class GapInfo(
    val courtId: String,
    val gapStartTime: String,
    val gapEndTime: String,
    val gapSlotsCount: Int = 1
) {
    fun formattedRange(): String {
        val startFormatted = formatTime(gapStartTime)
        val endFormatted = formatTime(gapEndTime)
        return "$startFormatted–$endFormatted"
    }

    private fun formatTime(time: String): String {
        return if (time.length >= 4) {
            "${time.substring(0, 2)}:${time.substring(2, 4)}"
        } else {
            time
        }
    }
}

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
            // Deselection flow: Tách khối thành các mảnh và giữ nguyên mọi mảnh còn lại (kể cả mảnh 1 khung)
            val newCourtSlots = targetCourtSlots.filterNot { it.slotId == clickedSlot.slotId }
            return updateStateWithSlots(otherCourtsSlots + newCourtSlots)
        } else {
            // Selection flow: Cap tối đa 8 khung tính theo TỪNG SÂN (FR-026)
            if (targetCourtSlots.size >= MAX_SLOTS_PER_COURT) {
                return currentSelection.copy(
                    errorMessage = "Sân đã đủ $MAX_SLOTS_PER_COURT khung (4 giờ)"
                )
            }

            // Bấm ô không liền kề trên cùng sân -> KHÔNG reset chuỗi nữa; mở khối mới trên sân đó
            val selectedSlot = clickedSlot.copy(status = SlotStatus.SELECTED)
            val newCourtSlots = (targetCourtSlots + selectedSlot).sortedBy { it.startTime }
            return updateStateWithSlots(otherCourtsSlots + newCourtSlots)
        }
    }

    /**
     * Kiểm tra điều kiện hợp lệ để giữ chỗ:
     * - MỖI sân có trong lựa chọn phải có từ 2 đến 8 khung giờ (1h đến 4h).
     * - Khoảng trống giữa 2 khối trên cùng sân: >= 2 khung (>= 1 giờ) thì được; đúng 1 khung (30 phút) thì CẤM.
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
            val invalidGaps = findSingleSlotGaps(courtSlots)
            if (invalidGaps.isNotEmpty()) {
                val gap = invalidGaps.first()
                return Result.failure(
                    IllegalArgumentException("$courtName đang trống lẻ 1 khung ${gap.formattedRange()} giữa 2 khối đã chọn. Khung này không thể bán cho người khác.")
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
            courtSlots.size in MIN_SLOTS_PER_COURT..MAX_SLOTS_PER_COURT &&
                findSingleSlotGaps(courtSlots).isEmpty()
        }
    }

    /**
     * Tìm sân đầu tiên có vấn đề để hiển thị gợi ý trên UI:
     * 1. Ưu tiên cảnh báo khoảng trống lẻ 1 khung.
     * 2. Cảnh báo chưa đủ 2 khung tối thiểu.
     */
    fun findIncompleteCourt(
        slots: List<TimeSlot>,
        courtNames: Map<String, String> = emptyMap()
    ): String? {
        if (slots.isEmpty()) return null
        val courtGroups = slots.groupBy { it.courtId }

        // Kiểm tra khoảng trống lẻ 1 khung trước
        for ((courtId, courtSlots) in courtGroups) {
            val invalidGaps = findSingleSlotGaps(courtSlots)
            if (invalidGaps.isNotEmpty()) {
                val name = courtNames[courtId] ?: "Sân"
                val gap = invalidGaps.first()
                return "Đang trống lẻ ${gap.formattedRange()} trên $name — khung này sẽ không ai đặt được. Thêm khung đó để nối liền, hoặc bỏ khối tiếp theo."
            }
        }

        // Kiểm tra chưa đủ tối thiểu 2 khung
        for ((courtId, courtSlots) in courtGroups) {
            if (courtSlots.size < MIN_SLOTS_PER_COURT) {
                val name = courtNames[courtId] ?: "Sân"
                return "$name cần thêm ${MIN_SLOTS_PER_COURT - courtSlots.size} khung giờ liên tiếp mới đặt được (mỗi sân tối thiểu 2 khung, tức 1 giờ)."
            }
        }
        return null
    }

    /**
     * Tìm tất cả các khoảng trống lẻ đúng 1 khung (30 phút) trên một sân
     */
    fun findSingleSlotGaps(courtSlots: List<TimeSlot>): List<GapInfo> {
        if (courtSlots.size < 2) return emptyList()
        val sorted = courtSlots.sortedBy { it.startTime }
        val gaps = mutableListOf<GapInfo>()
        for (i in 0 until sorted.size - 1) {
            val curr = sorted[i]
            val next = sorted[i + 1]
            if (curr.endTime != next.startTime) {
                val gapMinutes = timeToMinutes(next.startTime) - timeToMinutes(curr.endTime)
                if (gapMinutes == 30) {
                    gaps.add(
                        GapInfo(
                            courtId = curr.courtId,
                            gapStartTime = curr.endTime,
                            gapEndTime = next.startTime,
                            gapSlotsCount = 1
                        )
                    )
                }
            }
        }
        return gaps
    }

    private fun timeToMinutes(time: String): Int {
        if (time.length < 4) return 0
        val hour = time.substring(0, 2).toIntOrNull() ?: 0
        val minute = time.substring(2, 4).toIntOrNull() ?: 0
        return hour * 60 + minute
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

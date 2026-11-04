package com.example.booking_app.feature.booking.data.model

enum class SlotChangeTypeDto {
    ADDED,
    MODIFIED,
    REMOVED
}

data class SlotChangeDto(
    val type: SlotChangeTypeDto,
    val slot: SlotDto
)

data class BusySlotsUpdateDto(
    val allSlots: List<SlotDto>,
    val isInitial: Boolean,
    val changes: List<SlotChangeDto> = emptyList()
)

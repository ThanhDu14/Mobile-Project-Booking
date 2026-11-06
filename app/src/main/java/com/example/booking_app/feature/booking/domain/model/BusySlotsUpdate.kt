package com.example.booking_app.feature.booking.domain.model

enum class SlotChangeType {
    ADDED,
    MODIFIED,
    REMOVED
}

data class SlotChange(
    val type: SlotChangeType,
    val slot: TimeSlot
)

data class BusySlotsUpdate(
    val allBusySlots: List<TimeSlot>,
    val isInitial: Boolean,
    val changes: List<SlotChange> = emptyList()
)

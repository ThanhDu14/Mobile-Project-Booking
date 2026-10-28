package com.example.booking_app.feature.booking.domain.model

data class SlotSelectionState(
    val selectedSlots: List<TimeSlot> = emptyList(),
    val totalPrice: Long = 0L,
    val depositAmount: Long = 0L,
    val isValid: Boolean = false,
    val errorMessage: String? = null
) {
    val slotCount: Int get() = selectedSlots.size
}

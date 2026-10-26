package com.example.booking_app.feature.booking.ui

import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import java.time.LocalDate

data class BookingSlotUiState(
    val isLoading: Boolean = false,
    val venueId: String = "",
    val venueName: String = "",
    val operatingHours: String = "",
    val courts: List<Court> = emptyList(),
    val selectedCourtId: String? = null,
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val slots: List<TimeSlot> = emptyList(),
    val selectedSlotIds: Set<String> = emptySet(),
    val totalPrice: Long = 0L,
    val depositAmount: Long = 0L,
    val canProceed: Boolean = false,
    val isHolding: Boolean = false,
    val errorMessage: String? = null,
    val isClosed: Boolean = false
) {
    val totalAmount: Long get() = totalPrice
    val selectedSlotsCount: Int get() = selectedSlotIds.size
}

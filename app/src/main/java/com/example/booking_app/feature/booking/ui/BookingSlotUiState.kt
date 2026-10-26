package com.example.booking_app.feature.booking.ui

import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import java.time.LocalDate

data class SlotMatrixRow(
    val timeFormatted: String, // vd: "17:00"
    val timeRaw: String,       // vd: "1700"
    val slots: List<TimeSlot>  // Mỗi phần tử ứng với 1 sân con theo đúng thứ tự courts
)

data class BookingSlotUiState(
    val isLoading: Boolean = false,
    val venueId: String = "",
    val venueName: String = "",
    val venueAddress: String = "",
    val districtName: String = "",
    val operatingHours: String = "",
    val courts: List<Court> = emptyList(),
    val selectedCourtId: String? = null,
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val slots: List<TimeSlot> = emptyList(),
    val matrixRows: List<SlotMatrixRow> = emptyList(),
    val selectedSlots: List<TimeSlot> = emptyList(),
    val selectedSlotIds: Set<String> = emptySet(),
    val totalPrice: Long = 0L,
    val depositAmount: Long = 0L,
    val canProceed: Boolean = false,
    val isHolding: Boolean = false,
    val errorMessage: String? = null,
    val isClosed: Boolean = false,
    val isFullyBooked: Boolean = false,
    val peakHourBannerText: String? = null,
    val selectionDetailSummary: String = ""
) {
    val totalAmount: Long get() = totalPrice
    val selectedSlotsCount: Int get() = selectedSlotIds.size
}

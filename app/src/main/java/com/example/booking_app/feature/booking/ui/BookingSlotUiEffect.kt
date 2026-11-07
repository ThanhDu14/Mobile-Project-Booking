package com.example.booking_app.feature.booking.ui

import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.usecase.GapInfo

sealed interface BookingSlotUiEffect {
    data class ShowConflictWarning(val message: String) : BookingSlotUiEffect
    data class NavigateToBookingSummary(val args: HoldToPaymentArgs) : BookingSlotUiEffect
    data object NavigateToLogin : BookingSlotUiEffect
    data class ShowToast(val message: String) : BookingSlotUiEffect
    data class ShowErrorSnackbar(val message: String) : BookingSlotUiEffect
    data class ShowGapResolutionDialog(
        val courtName: String,
        val gapInfo: GapInfo,
        val canAddSlot: Boolean,
        val gapSlotToAdd: TimeSlot?,
        val trailingBlockSlotsToRemove: List<TimeSlot>,
        val trailingBlockRange: String
    ) : BookingSlotUiEffect
}

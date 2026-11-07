package com.example.booking_app.feature.booking.ui

sealed interface BookingSlotUiEffect {
    data class ShowConflictWarning(val message: String) : BookingSlotUiEffect
    data class NavigateToBookingSummary(val args: HoldToPaymentArgs) : BookingSlotUiEffect
    data object NavigateToLogin : BookingSlotUiEffect
    data class ShowToast(val message: String) : BookingSlotUiEffect
    data class ShowErrorSnackbar(val message: String) : BookingSlotUiEffect
}

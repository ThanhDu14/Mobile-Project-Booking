package com.example.booking_app.feature.booking.ui.state

import com.example.booking_app.feature.booking.ui.HoldToPaymentArgs

sealed interface BookingRecurringUiEffect {
    data class ShowToast(val message: String) : BookingRecurringUiEffect
    data class ShowErrorDialog(val title: String, val message: String) : BookingRecurringUiEffect
    data class NavigateToConfirm(val recurringGroupId: String) : BookingRecurringUiEffect
    data class NavigateToPayment(val args: HoldToPaymentArgs) : BookingRecurringUiEffect
    data object NavigateBackToGrid : BookingRecurringUiEffect
    data object NavigateToLogin : BookingRecurringUiEffect
    data object PopBack : BookingRecurringUiEffect
}

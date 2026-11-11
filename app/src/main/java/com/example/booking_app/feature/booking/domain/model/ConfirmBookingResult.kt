package com.example.booking_app.feature.booking.domain.model

sealed class ConfirmBookingResult {
    data class Success(val bookingId: String) : ConfirmBookingResult()
    object Expired : ConfirmBookingResult()
    data class Error(val message: String, val isPermissionDenied: Boolean = false) : ConfirmBookingResult()
}

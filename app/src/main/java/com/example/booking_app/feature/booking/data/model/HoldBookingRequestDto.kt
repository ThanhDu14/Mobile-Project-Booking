package com.example.booking_app.feature.booking.data.model

data class HoldBookingRequestDto(
    val venueId: String,
    val courtId: String,
    val date: String,
    val slotIds: List<String>,
    val userId: String,
    val requestId: String
)

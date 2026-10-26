package com.example.booking_app.feature.booking.domain.model

data class VenueInfo(
    val id: String,
    val name: String,
    val address: String,
    val districtName: String,
    val openTime: String,
    val closeTime: String
)

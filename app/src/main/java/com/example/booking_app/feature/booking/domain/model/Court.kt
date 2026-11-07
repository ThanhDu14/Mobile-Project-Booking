package com.example.booking_app.feature.booking.domain.model

data class Court(
    val id: String,
    val name: String,
    val surfaceType: String,
    val isActive: Boolean,
    val sortOrder: Int
)

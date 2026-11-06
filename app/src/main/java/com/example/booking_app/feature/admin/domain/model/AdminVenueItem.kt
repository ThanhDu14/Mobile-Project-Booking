package com.example.booking_app.feature.admin.domain.model

data class AdminVenueItem(
    val venueId: String,
    val name: String,
    val address: String,
    val status: String,
    val ownerId: String,
) {
    val isLocked: Boolean get() = status.equals("LOCKED", ignoreCase = true)
}

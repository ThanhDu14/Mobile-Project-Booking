package com.example.booking_app.feature.booking.data.model

import com.google.firebase.firestore.DocumentId

data class CourtDto(
    @DocumentId val id: String = "",
    val name: String = "",
    val surfaceType: String = "",
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)

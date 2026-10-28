package com.example.booking_app.feature.booking.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class SlotDto(
    @DocumentId val id: String = "",
    val venueId: String = "",
    val courtId: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val status: String = "AVAILABLE",
    val blockReason: String? = null,
    val holdExpiresAt: Timestamp? = null,
    val holdBy: String? = null,
    val bookingId: String? = null
)

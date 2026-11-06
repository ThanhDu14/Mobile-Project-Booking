package com.example.booking_app.feature.booking.data.model

import com.google.firebase.firestore.DocumentId

data class BookingSessionDto(
    @DocumentId val id: String = "",
    val recurringGroupId: String = "",
    val userId: String = "",
    val venueId: String = "",
    val courtId: String = "",
    val courtName: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val slotIds: List<String> = emptyList(),
    val status: String = "HOLD",
    val price: Long = 0L,
    val depositAmount: Long = 0L
)

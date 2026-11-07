package com.example.booking_app.feature.booking.data.model

import com.google.firebase.firestore.DocumentId

data class PriceRuleDto(
    @DocumentId val id: String = "",
    val daysOfWeek: List<Int> = emptyList(),
    val startTime: String = "",
    val endTime: String = "",
    val pricePerHour: Long = 0L,
    val label: String = "NORMAL"
)

package com.example.booking_app.feature.booking.domain.model

data class TimeSlot(
    val slotId: String,
    val courtId: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val price: Long,
    val status: SlotStatus,
    val blockReason: String? = null,
    val holdExpiresAtMillis: Long? = null
)

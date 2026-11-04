package com.example.booking_app.feature.booking.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class RecurringGroupDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val venueId: String = "",
    val venueName: String = "",
    val daysOfWeek: List<Int> = emptyList(),
    val startTime: String = "",
    val endTime: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val totalSessions: Int = 0,
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val total: Long = 0L,
    val depositAmount: Long = 0L,
    val paymentStatus: String = "PENDING",
    val status: String = "HOLD",
    val holdExpiresAt: Timestamp? = null,
    val requestId: String = "",
    val createdAt: Timestamp? = null
)

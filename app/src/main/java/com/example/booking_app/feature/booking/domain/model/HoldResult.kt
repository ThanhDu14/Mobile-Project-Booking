package com.example.booking_app.feature.booking.domain.model

sealed interface HoldResult {
    data class Success(val bookingId: String, val holdExpiresAtMillis: Long) : HoldResult
    data class Conflict(val conflictedSlotIds: List<String>) : HoldResult
}

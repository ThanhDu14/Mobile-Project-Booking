package com.example.booking_app.feature.booking_management.data

import com.google.firebase.Timestamp

/**
 * Data Transfer Object matching the shared Firestore schema for `bookings/{bookingId}`.
 * Follows docs/design/database/README.md and spec 060.
 */
data class BookingDto(
    val userId: String = "",
    val venueId: String = "",
    val venueSnapshot: VenueSnapshotDto = VenueSnapshotDto(),
    val items: List<BookingSlotItemDto> = emptyList(),
    val startAt: Timestamp? = null,
    val endAt: Timestamp? = null,
    val status: String = "",
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val total: Long = 0L,
    val depositAmount: Long = 0L,
    val paidAmount: Long = 0L,
    val remainingAmount: Long = 0L,
    val paymentMethod: String = "",
    val paymentStatus: String = "",
    val recurringGroupId: String? = null,
    val qrToken: String? = null,
    val checkedInAt: Timestamp? = null,
    val holdExpiresAt: Timestamp? = null,
    val approvalDeadline: Timestamp? = null,
    val cancelReason: String? = null,
    val cancelledBy: String? = null,
    val cancelledAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val refundAmount: Long? = null,
    val penaltyAmount: Long? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class VenueSnapshotDto(
    val name: String = "",
    val address: String = "",
    val phone: String? = null
)

data class BookingSlotItemDto(
    val slotId: String = "",
    val courtId: String = "",
    val courtName: String = "",
    val startAt: Timestamp? = null,
    val endAt: Timestamp? = null,
    val price: Long = 0L
)

package com.example.booking_app.feature.owner.domain.booking

import java.time.Duration
import java.time.Instant

/** Normalized owner-safe read model. It is not a Firestore DTO and never contains qrToken. */
data class OwnerBooking(
    val bookingId: String,
    val venueId: String,
    val status: OwnerBookingStatus,
    val startAt: Instant,
    val endAt: Instant,
    val approvalDeadline: Instant? = null,
    val customerDisplayName: String? = null,
    val courtNames: List<String> = emptyList(),
    val paymentMethod: String? = null,
    val paymentStatus: String? = null,
    val displayAmountVnd: Long? = null,
    val depositAmountVnd: Long? = null,
    val checkedInAt: Instant? = null,
)

enum class OwnerBookingStatus {
    HOLD,
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    COMPLETED,
    NO_SHOW,
}

enum class OwnerBookingFilter {
    PENDING,
    UPCOMING,
    PAST,
}

fun OwnerBooking.approvalTimeRemaining(now: Instant): Duration? =
    approvalDeadline?.let { deadline -> Duration.between(now, deadline).coerceAtLeast(Duration.ZERO) }

/** The server remains authoritative for status, deadline, venue and slot validation. */
sealed interface OwnerBookingCommand {
    val venueId: String
    val bookingId: String

    data class Approve(
        override val venueId: String,
        override val bookingId: String,
    ) : OwnerBookingCommand

    data class Reject(
        override val venueId: String,
        override val bookingId: String,
    ) : OwnerBookingCommand

    data class CancelConfirmed(
        override val venueId: String,
        override val bookingId: String,
        val reason: String,
    ) : OwnerBookingCommand
}

data class OwnerBookingDetail(
    val booking: OwnerBooking,
    val slotCount: Int,
    val subtotalVnd: Long?,
    val discountVnd: Long?,
    val refundVnd: Long?,
    val cancelReason: String?,
)

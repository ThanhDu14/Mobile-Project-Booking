package com.example.booking_app.feature.account.domain.model

import java.time.Instant

/** Bản xem trước của `delete-account` (action `preview`). Tiền là VND. */
data class DeletionImpact(
    val impactVersion: String,
    val myBookings: List<ImpactBooking>,
    val myDropIns: List<ImpactDropIn>,
    val waitlistCount: Int,
    val ownerImpact: OwnerImpact?,
    val walletBalance: Long,
)

data class ImpactBooking(
    val bookingId: String,
    val venueName: String,
    val startAt: Instant,
    val status: String,
    val paidAmount: Long,
    val refundAmount: Long,
)

data class ImpactDropIn(
    val sessionId: String,
    val venueName: String,
    val startAt: Instant,
    val paidAmount: Long,
    val refundAmount: Long,
)

data class OwnerImpact(
    val venueCount: Int,
    val guestBookingCount: Int,
    val guestDropInCount: Int,
    val guestCount: Int,
)

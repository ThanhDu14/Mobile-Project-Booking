package com.example.booking_app.feature.booking.domain

import java.time.Instant

/** Active bookings that overlap the half-open interval [from, to). */
interface UpcomingBookingsReader {
    suspend fun upcoming(uid: String, from: Instant, to: Instant): List<UpcomingBooking>
}

data class UpcomingBooking(
    val bookingId: String,
    val venueId: String?,
    val startAt: Instant,
    val endAt: Instant,
)

package com.example.booking_app.feature.owner.domain.booking

import java.time.Instant

class OwnerBookingQueue {
    fun filter(
        bookings: List<OwnerBooking>,
        filter: OwnerBookingFilter,
        now: Instant,
    ): List<OwnerBooking> = when (filter) {
        OwnerBookingFilter.PENDING -> bookings
            .filter { it.status == OwnerBookingStatus.PENDING }
            .sortedWith(compareBy(nullsLast()) { it.approvalDeadline })

        OwnerBookingFilter.UPCOMING -> bookings
            .filter { it.status == OwnerBookingStatus.CONFIRMED && it.endAt.isAfter(now) }
            .sortedBy { it.startAt }

        OwnerBookingFilter.PAST -> bookings
            .filter {
                it.status in setOf(
                    OwnerBookingStatus.REJECTED,
                    OwnerBookingStatus.CANCELLED,
                    OwnerBookingStatus.EXPIRED,
                    OwnerBookingStatus.COMPLETED,
                    OwnerBookingStatus.NO_SHOW,
                ) || (it.status == OwnerBookingStatus.CONFIRMED && it.endAt <= now)
            }
            .sortedByDescending { it.startAt }
    }
}

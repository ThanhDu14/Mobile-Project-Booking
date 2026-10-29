package com.example.booking_app.feature.owner.domain

/** A typed client-side explanation for writes intentionally kept behind an external contract gate. */
enum class OwnerWriteGate {
    SLOT_ATOMIC_SERVER_CONTRACT,
    MIN_PRICE_TRUSTED_AUTHORITY,
    VENUE_LIFECYCLE
}

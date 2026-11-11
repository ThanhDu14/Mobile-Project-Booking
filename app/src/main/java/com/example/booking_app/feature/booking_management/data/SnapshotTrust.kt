package com.example.booking_app.feature.booking_management.data

/** Cache and local writes cannot establish the current booking state or QR eligibility. */
internal fun isAuthoritativeSnapshot(isFromCache: Boolean, hasPendingWrites: Boolean): Boolean =
    !isFromCache && !hasPendingWrites

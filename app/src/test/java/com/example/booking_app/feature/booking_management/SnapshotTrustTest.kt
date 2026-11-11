package com.example.booking_app.feature.booking_management

import com.example.booking_app.feature.booking_management.data.isAuthoritativeSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTrustTest {
    @Test fun onlyServerSnapshotsWithoutPendingWritesAreAccepted() {
        assertTrue(isAuthoritativeSnapshot(isFromCache = false, hasPendingWrites = false))
        assertFalse(isAuthoritativeSnapshot(isFromCache = true, hasPendingWrites = false))
        assertFalse(isAuthoritativeSnapshot(isFromCache = false, hasPendingWrites = true))
        assertFalse(isAuthoritativeSnapshot(isFromCache = true, hasPendingWrites = true))
    }
}

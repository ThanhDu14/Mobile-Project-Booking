package com.example.booking_app.feature.owner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerErrorClassifierTest {
    @Test fun classifiesPermissionNetworkAndConflictWithoutRetryPolicy() {
        assertEquals(OwnerErrorKind.PERMISSION, OwnerErrorClassifier.kind(IllegalStateException("permission denied")))
        assertEquals(OwnerErrorKind.NETWORK, OwnerErrorClassifier.kind(IllegalStateException("timeout while saving")))
        assertEquals(OwnerErrorKind.CONFLICT, OwnerErrorClassifier.kind(IllegalStateException("stale conflict")))
    }
}

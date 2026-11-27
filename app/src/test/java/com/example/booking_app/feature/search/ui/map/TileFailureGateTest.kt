package com.example.booking_app.feature.search.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileFailureGateTest {
    @Test fun successfulTileCancelsPendingFailure() {
        val gate = TileFailureGate()
        gate.setVisible(true)
        assertTrue(gate.failure())
        assertFalse(gate.failure())
        gate.success()
        assertFalse(gate.timeout())
        assertFalse(gate.failure())
    }

    @Test fun failedLoadCanBeRetriedAfterLeavingMap() {
        val gate = TileFailureGate()
        assertFalse(gate.failure())
        gate.setVisible(true)
        assertTrue(gate.failure())
        assertTrue(gate.timeout())
        assertFalse(gate.timeout())
        gate.setVisible(false)
        gate.setVisible(true)
        assertTrue(gate.failure())
        gate.setVisible(false)
        assertFalse(gate.timeout())
    }
}

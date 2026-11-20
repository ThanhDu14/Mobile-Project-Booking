package com.example.booking_app.core.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VenueAreaTest {

    @Test
    fun placesInVietnamAreAccepted() {
        assertTrue(VenueArea.contains(10.8128, 106.6283)) // TP.HCM
        assertTrue(VenueArea.contains(21.0285, 105.8542)) // Hà Nội
        assertTrue(VenueArea.contains(10.2899, 103.9840)) // Phú Quốc
        assertTrue(VenueArea.contains(8.6830, 106.6070)) // Côn Đảo
    }

    @Test
    fun obviousMistakesAreRejected() {
        assertFalse(VenueArea.contains(90.0, 90.0))
        assertFalse(VenueArea.contains(0.0, 0.0))
        assertFalse(VenueArea.contains(106.6283, 10.8128)) // đảo vĩ độ và kinh độ
        assertFalse(VenueArea.contains(Double.NaN, 106.6))
    }
}

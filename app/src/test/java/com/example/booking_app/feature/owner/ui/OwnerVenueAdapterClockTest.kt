package com.example.booking_app.feature.owner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerVenueAdapterClockTest {
    @Test fun `HHmm is shown with a colon`() {
        assertEquals("04:00", OwnerVenueAdapter.clock("0400"))
        assertEquals("24:00", OwnerVenueAdapter.clock("2400"))
    }

    @Test fun `already formatted or unexpected values are kept`() {
        assertEquals("05:30", OwnerVenueAdapter.clock("05:30"))
        assertEquals("", OwnerVenueAdapter.clock(""))
    }
}

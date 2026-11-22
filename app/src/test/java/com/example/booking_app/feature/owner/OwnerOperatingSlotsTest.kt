package com.example.booking_app.feature.owner

import com.example.booking_app.feature.owner.domain.OwnerOperatingSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerOperatingSlotsTest {
    @Test fun closingAt2359NeverGeneratesWrappedMidnightSlot() {
        assertEquals(listOf(1380), OwnerOperatingSlots.startMinutes("2300", "2359"))
    }
    @Test fun openingBetweenBoundariesStartsAtNextCanonicalHalfHour() {
        assertEquals(listOf(330,360,390), OwnerOperatingSlots.startMinutes("0520", "0700"))
    }
    @Test fun intervalTooShortForOneWholeSlotIsEmpty() {
        assertTrue(OwnerOperatingSlots.startMinutes("2350", "2359").isEmpty())
        assertTrue(OwnerOperatingSlots.startMinutes("0601", "0630").isEmpty())
    }
    @Test fun midnightOpeningAndExactClosingBoundaryAreIncludedCorrectly() {
        assertEquals(listOf(0), OwnerOperatingSlots.startMinutes("0000", "0030"))
    }
    @Test fun invalidOrOvernightHoursFailInsteadOfFabricatingAvailability() {
        for ((open,close) in listOf("2400" to "2500", "2200" to "0600", "oops" to "2200")) {
            assertTrue(runCatching { OwnerOperatingSlots.startMinutes(open,close) }.isFailure)
        }
    }
}

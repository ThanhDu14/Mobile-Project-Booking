package com.example.booking_app.feature.owner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerPriceRuleAdapterFormatTest {
    @Test fun `three or more consecutive days collapse into a range`() {
        assertEquals("T2–T6", OwnerPriceRuleAdapter.formatDays(listOf(5, 1, 2, 3, 4)))
        assertEquals("T2–CN", OwnerPriceRuleAdapter.formatDays((1..7).toList()))
    }

    @Test fun `short runs stay as single days`() {
        assertEquals("T7, CN", OwnerPriceRuleAdapter.formatDays(listOf(7, 6)))
        assertEquals("T2, T4–T6", OwnerPriceRuleAdapter.formatDays(listOf(1, 3, 4, 5)))
    }
}

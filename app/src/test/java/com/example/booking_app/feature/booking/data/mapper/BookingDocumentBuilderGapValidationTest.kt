package com.example.booking_app.feature.booking.data.mapper

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingDocumentBuilderGapValidationTest {

    @Test
    fun `gap 1 slot (30 mins) throws IllegalArgumentException`() {
        // court4_20261109_2000 (ends 20:30) and court4_20261109_2100 (starts 21:00) -> gap is 30 mins
        val slotIds = listOf(
            "court4_20261109_2000",
            "court4_20261109_2100"
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            BookingDocumentBuilder.validateSlotGaps(slotIds)
        }
        assertTrue(exception.message?.contains("30 phút") == true)
    }

    @Test
    fun `gap 2 slots (60 mins) passes validation`() {
        // court4_20261109_2000 (ends 20:30) and court4_20261109_2130 (starts 21:30) -> gap is 60 mins (2 slots)
        val slotIds = listOf(
            "court4_20261109_2000",
            "court4_20261109_2130"
        )

        // Should not throw
        BookingDocumentBuilder.validateSlotGaps(slotIds)
    }

    @Test
    fun `1 continuous block passes validation`() {
        val slotIds = listOf(
            "court4_20261109_2000",
            "court4_20261109_2030",
            "court4_20261109_2100"
        )

        // Should not throw
        BookingDocumentBuilder.validateSlotGaps(slotIds)
    }

    @Test
    fun `single slot passes validation`() {
        val slotIds = listOf("court4_20261109_2000")
        BookingDocumentBuilder.validateSlotGaps(slotIds)
    }

    @Test
    fun `gap on different courts does not interfere`() {
        // Court 1 has 20:00, Court 2 has 20:30
        val slotIds = listOf(
            "court1_20261109_2000",
            "court2_20261109_2030"
        )
        // Each court only has 1 slot -> passes
        BookingDocumentBuilder.validateSlotGaps(slotIds)
    }
}

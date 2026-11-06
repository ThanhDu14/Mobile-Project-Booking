package com.example.booking_app.feature.owner.domain.dropin

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class OwnerDropInDraftValidationTest {
    @Test fun acceptsValidSessionOnThirtyMinuteBoundaryWithoutClientManagedCount() {
        assertTrue(OwnerDropInDraftValidation.validate(draft()).isEmpty())
    }

    @Test fun rejectsMissingVenueCourtLevelAndInvalidCapacityOrPrice() {
        val errors = OwnerDropInDraftValidation.validate(
            draft(venueId = "", courtIds = emptyList(), level = "", capacity = 0, price = -1),
        )
        assertTrue(errors.any { it.contains("cơ sở") })
        assertTrue(errors.any { it.contains("sân") })
        assertTrue(errors.any { it.contains("trình độ") })
        assertTrue(errors.any { it.contains("Sức chứa") })
        assertTrue(errors.any { it.contains("Giá") })
    }

    @Test fun rejectsInvalidDateTimeAndSlotAlignment() {
        val errors = OwnerDropInDraftValidation.validate(draft(date = "2026-11-05", start = "23:30", end = "00:30"))
        assertFalse(errors.isEmpty())
        assertTrue(errors.any { it.contains("Ngày") })
        assertTrue(errors.any { it.contains("Giờ") })
    }

    @Test fun rejectsNonThirtyMinuteTimes() {
        val errors = OwnerDropInDraftValidation.validate(draft(start = "18:15", end = "19:15"))
        assertTrue(errors.any { it.contains("30 phút") })
    }

    private fun draft(
        venueId: String = "venue-1",
        courtIds: List<String> = listOf("court-1"),
        date: String = "20261105",
        start: String = "18:00",
        end: String = "19:00",
        level: String = "BEGINNER",
        capacity: Int = 8,
        price: Long = 30_000,
    ) = OwnerDropInDraft(venueId, courtIds, date, start, end, level, capacity, price, "Ghi chú")
}

package com.example.booking_app.feature.owner.domain

import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule

class OwnerValidationTest {
    @Test fun venueRequiresPhotoAndValidVietnamesePhone() {
        val errors = OwnerValidation.venue(VenueInput("Sân", "Q1", "d1", GeoPoint(10.7, 106.7), "0600", "2200", "0123456789", emptyList(), emptyList(), 2))
        assertTrue(errors.any { it.field == "photoPaths" })
        assertTrue(errors.any { it.field == "phone" })
    }

    @Test fun validCourtAndVenueValuesPass() {
        assertTrue(OwnerValidation.court(CourtInput("Sân 1", "MAT", 0)).isEmpty())
        assertTrue(OwnerValidation.venue(VenueInput("Sân", "Q1", "d1", GeoPoint(10.7, 106.7), "0600", "2200", "0912345678", emptyList(), listOf("venue/a.jpg"), 2)).isEmpty())
    }

    @Test fun courtRejectsUnknownSurfaceAndNegativeOrderWithoutApplyingNamePolicy() {
        val errors = OwnerValidation.court(CourtInput("Sân trùng", "CONCRETE", -1))
        assertEquals(setOf("surfaceType", "sortOrder"), errors.map { it.field }.toSet())
    }

    @Test fun priceRuleRejectsInvalidPriceAndOverlap() {
        val existing = PriceRuleInput(listOf(1, 2), "0600", "0800", 40_000, "NORMAL")
        val errors = OwnerValidation.priceRule(PriceRuleInput(listOf(2), "0730", "0900", 41_000, "BAD"), listOf(existing))
        assertEquals(setOf("pricePerHour", "label", "timeRange"), errors.map { it.field }.toSet())
    }

    @Test fun slotRangeMapsEveryHalfHourAndRejectsOvernight() {
        val input = SlotBlockInput("c1", "20261029", "0600", "0730", "MAINTENANCE")
        assertEquals(listOf("c1_20261029_0600", "c1_20261029_0630", "c1_20261029_0700"), OwnerValidation.slotIds(input))
        assertTrue(OwnerValidation.slotBlock(input.copy(startTime = "2300", endTime = "0030")).isNotEmpty())
    }
    @Test fun priceCoverageUsesHalfHourPriceAndReportsGaps() {
        val rule = OwnerPriceRule("r1", listOf(4), "0600", "0700", 80_000, "NORMAL")
        assertEquals(40_000L, PriceRuleCoverage.pricePerSlot(rule.pricePerHour))
        assertEquals("r1", PriceRuleCoverage.ruleFor(4, "0630", listOf(rule))?.id)
        assertEquals(listOf("0700", "0730"), PriceRuleCoverage.missingHalfHourStarts(4, "0600", "0800", listOf(rule)))
        assertTrue(PriceRuleCoverage.deletionLeavesCoverageGap("r1", 4, "0600", "0800", listOf(rule)))
    }

    @Test fun priceCoverageDoesNotMutateHistoricalSnapshot() {
        val historicalPrice = 40_000L
        val rules = listOf(OwnerPriceRule("r1", listOf(4), "0600", "0700", 80_000, "NORMAL"))
        PriceRuleCoverage.missingHalfHourStarts(4, "0600", "0800", rules)
        assertEquals(40_000L, historicalPrice)
    }
}

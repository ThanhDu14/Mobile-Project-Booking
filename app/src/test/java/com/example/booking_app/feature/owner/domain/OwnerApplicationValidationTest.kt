package com.example.booking_app.feature.owner.domain

import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerApplicationValidationTest {
    @Test
    fun validApplicantDraftPasses() {
        assertTrue(OwnerApplicationValidation.validate(validDraft()).isEmpty())
    }

    @Test
    fun missingAndInvalidFieldsAreReportedByField() {
        val draft = validDraft().copy(
            venue = validDraft().venue.copy(
                name = "  ", districtId = "", location = null,
                courtCount = 0, openTime = "25:00", closeTime = "25:30", photoPaths = emptyList(),
            ),
            representativeName = "\t", representativePhone = "abc", documentPaths = emptyList(),
        )

        assertEquals(
            setOf("venueName", "districtId", "location", "courtCount", "openTime", "closeTime", "representativeName", "representativePhone", "photoPaths", "documentPaths"),
            OwnerApplicationValidation.validate(draft).map { it.field }.toSet(),
        )
    }

    @Test
    fun rejectsClosingTimeBeforeOpeningTime() {
        val errors = OwnerApplicationValidation.validate(validDraft().copy(venue = validDraft().venue.copy(openTime = "22:00", closeTime = "05:00")))
        assertTrue(errors.any { it.field == "closeTime" })
    }

    @Test
    fun evidenceRequiresSupportedImageAndAtMostTwoMegabytes() {
        assertTrue(OwnerApplicationValidation.validateEvidence(OwnerEvidenceFile("image/jpeg", 2L * 1024 * 1024)).isEmpty())
        assertEquals("evidence", OwnerApplicationValidation.validateEvidence(OwnerEvidenceFile("application/pdf", 1)).single().field)
        assertEquals("evidence", OwnerApplicationValidation.validateEvidence(OwnerEvidenceFile("image/png", 2L * 1024 * 1024 + 1)).single().field)
    }

    private fun validDraft() = OwnerApplicationDraft(
        venue = OwnerVenueDraft(
            name = "Sân cầu lông Nam Phát", address = "88 Sư Vạn Hạnh", districtId = "district-10",
            location = GeoPoint(10.7731, 106.6672), phone = "028 3862 1145", courtCount = 5,
            openTime = "05:30", closeTime = "22:30", photoPaths = listOf("pending-uploaded-photo-path"),
        ),
        representativeName = "Nguyễn Thị Lan", representativePhone = "0903 412 778",
        documentPaths = listOf("pending-uploaded-document-path"),
    )
}

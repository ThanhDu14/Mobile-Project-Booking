package com.example.booking_app.feature.owner

import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationField
import com.example.booking_app.feature.owner.domain.OwnerApplicationValidator
import com.example.booking_app.feature.owner.domain.VenueDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerApplicationValidatorTest {
    @Test
    fun `draft co du truong va tai lieu hop le`() {
        assertTrue(OwnerApplicationValidator.validate(validDraft()).isValid)
    }

    @Test
    fun `noi dung chi co khoang trang bi bao loi va giu loi theo truong`() {
        val draft = validDraft().copy(
            venue = validDraft().venue.copy(name = "  ", latitude = 91.0, courtCount = 0),
            representativePhone = "\t",
            documentPaths = emptyList(),
        )

        val errors = OwnerApplicationValidator.validate(draft).errors
        assertEquals("required", errors[OwnerApplicationField.VENUE_NAME])
        assertEquals("invalid_location", errors[OwnerApplicationField.LOCATION])
        assertEquals("positive_integer_required", errors[OwnerApplicationField.COURT_COUNT])
        assertEquals("required", errors[OwnerApplicationField.REPRESENTATIVE_PHONE])
        assertEquals("required", errors[OwnerApplicationField.DOCUMENTS])
    }

    @Test
    fun `khong tu dat gioi han so san va khong chuan hoa phone`() {
        val draft = validDraft().copy(
            venue = validDraft().venue.copy(courtCount = Int.MAX_VALUE, phone = "  0123  "),
            representativePhone = "  0987  ",
        )

        assertTrue(OwnerApplicationValidator.validate(draft).isValid)
        assertEquals("  0123  ", draft.venue.phone)
        assertEquals("  0987  ", draft.representativePhone)
    }

    @Test
    fun `toa do khong huu han va gio sai dinh dang bi tu choi`() {
        val draft = validDraft().copy(
            venue = validDraft().venue.copy(latitude = Double.NaN, closeTime = "06:00"),
        )
        val errors = OwnerApplicationValidator.validate(draft).errors
        assertTrue(OwnerApplicationField.LOCATION in errors)
        assertTrue(OwnerApplicationField.CLOSING_TIME in errors)
    }

    @Test
    fun `cac truong bat buoc deu co loi rieng khi bi thieu`() {
        val draft = validDraft().copy(
            venue = validDraft().venue.copy(
                name = " ",
                address = "",
                districtId = "\t",
                latitude = null,
                longitude = null,
                phone = "",
                courtCount = null,
                openTime = "",
                closeTime = "",
                photoPaths = listOf(" "),
            ),
            representativeName = "",
            representativePhone = " ",
            documentPaths = emptyList(),
        )

        val errors = OwnerApplicationValidator.validate(draft).errors

        assertEquals(
            setOf(
                OwnerApplicationField.VENUE_NAME,
                OwnerApplicationField.ADDRESS,
                OwnerApplicationField.DISTRICT,
                OwnerApplicationField.LOCATION,
                OwnerApplicationField.VENUE_PHONE,
                OwnerApplicationField.COURT_COUNT,
                OwnerApplicationField.OPENING_TIME,
                OwnerApplicationField.CLOSING_TIME,
                OwnerApplicationField.REPRESENTATIVE_NAME,
                OwnerApplicationField.REPRESENTATIVE_PHONE,
                OwnerApplicationField.VENUE_PHOTOS,
                OwnerApplicationField.DOCUMENTS,
            ),
            errors.keys,
        )
    }

    @Test
    fun `gio hop le dung format HHmm cua schema venue`() {
        assertTrue(OwnerApplicationValidator.validate(validDraft()).isValid)
    }

    private fun validDraft() = OwnerApplicationDraft(
        venue = VenueDraft(
            name = "Sân Cầu Lông",
            address = "1 Đường A",
            districtId = "district-id",
            latitude = 10.78,
            longitude = 106.69,
            phone = "0900 000 000",
            courtCount = 2,
            openTime = "0600",
            closeTime = "2200",
            photoPaths = listOf("venue/path.jpg"),
        ),
        representativeName = "Nguyễn An",
        representativePhone = "0900 000 001",
        documentPaths = listOf("uid/application/document.jpg"),
    )
}

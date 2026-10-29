package com.example.booking_app.feature.owner.ui.form

import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationField
import com.example.booking_app.feature.owner.domain.VenueDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerApplicationFormViewModelTest {
    @Test
    fun `validation error hien thi ma van giu nguyen draft`() {
        val viewModel = OwnerApplicationFormViewModel()
        val draft = validDraft().copy(venue = validDraft().venue.copy(name = " "))
        viewModel.onDraftChanged(draft)

        val result = viewModel.validateDraft()

        assertFalse(result?.isValid ?: true)
        assertEquals(" ", viewModel.uiState.value.draft?.venue?.name)
        assertTrue(OwnerApplicationField.VENUE_NAME in viewModel.uiState.value.validation!!.errors)
    }

    @Test
    fun `draft con lai duoc giu sau khi nguoi dung sua truong loi`() {
        val viewModel = OwnerApplicationFormViewModel()
        viewModel.onDraftChanged(validDraft().copy(venue = validDraft().venue.copy(name = "")))
        viewModel.validateDraft()

        val corrected = viewModel.uiState.value.draft!!.copy(
            venue = viewModel.uiState.value.draft!!.venue.copy(name = "Sân mới"),
        )
        viewModel.onDraftChanged(corrected)

        assertEquals("Sân mới", viewModel.uiState.value.draft?.venue?.name)
        assertNotNull(viewModel.uiState.value.draft?.venue?.photoPaths?.single())
        assertTrue(viewModel.uiState.value.validation == null)
    }

    @Test
    fun `draft hop le phat su kien dieu huong den review`() = runBlocking {
        val viewModel = OwnerApplicationFormViewModel()
        viewModel.onDraftChanged(validDraft())

        val validation = viewModel.continueToReview()
        val event = viewModel.events.first()

        assertTrue(validation?.isValid == true)
        assertEquals(OwnerApplicationFormEvent.NavigateToReview, event)
    }

    @Test
    fun `draft khong hop le giu nguoi dung o form va khong phat su kien review`() {
        val viewModel = OwnerApplicationFormViewModel()
        val invalidDraft = validDraft().copy(
            venue = validDraft().venue.copy(name = "", photoPaths = emptyList()),
        )
        viewModel.onDraftChanged(invalidDraft)

        val validation = viewModel.continueToReview()

        assertFalse(validation?.isValid ?: true)
        assertEquals(invalidDraft, viewModel.uiState.value.draft)
        assertTrue(OwnerApplicationField.VENUE_NAME in viewModel.uiState.value.validation!!.errors)
        assertTrue(OwnerApplicationField.VENUE_PHOTOS in viewModel.uiState.value.validation!!.errors)
    }

    @Test
    fun `rejected identity reason and draft survive form edits`() {
        val viewModel = OwnerApplicationFormViewModel()
        val rejectedDraft = validDraft()
        viewModel.beginRejectedEdit("rejected-id", "Thiếu giấy tờ", rejectedDraft)
        viewModel.onDraftChanged(rejectedDraft.copy(representativeName = "Tên đã sửa"))

        assertEquals("rejected-id", viewModel.uiState.value.applicationId)
        assertEquals("Thiếu giấy tờ", viewModel.uiState.value.rejectReason)
        assertEquals("Tên đã sửa", viewModel.uiState.value.draft?.representativeName)
    }

    @Test
    fun `new application clears rejected identity and old document paths but keeps editable text`() {
        val viewModel = OwnerApplicationFormViewModel()
        viewModel.beginRejectedEdit("old-id", "Lý do", validDraft())

        viewModel.beginNewApplication()

        assertEquals(null, viewModel.uiState.value.applicationId)
        assertEquals(null, viewModel.uiState.value.rejectReason)
        assertEquals("Sân Cầu Lông", viewModel.uiState.value.draft?.venue?.name)
        assertTrue(viewModel.uiState.value.draft?.venue?.photoPaths.isNullOrEmpty())
        assertTrue(viewModel.uiState.value.draft?.documentPaths.isNullOrEmpty())
    }

    @Test
    fun `khong co draft thi khong the mo review`() {
        val viewModel = OwnerApplicationFormViewModel()

        assertEquals(null, viewModel.continueToReview())
        assertEquals(null, viewModel.uiState.value.draft)
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

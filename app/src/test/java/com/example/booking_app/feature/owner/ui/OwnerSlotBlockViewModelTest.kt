package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.feature.owner.domain.SlotBlockInput
import com.example.booking_app.feature.owner.domain.OwnerWriteGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerSlotBlockViewModelTest {
    private fun vm() = OwnerSlotBlockViewModel(SavedStateHandle(mapOf("courtId" to "court-1")))

    @Test fun previewMapsRangeAndSubmitDoesNotWriteBeforeT007() {
        val viewModel = vm()
        viewModel.updateForm(SlotBlockInput("court-1", "20261029", "0600", "0730", "EVENT"))
        viewModel.preview()
        assertEquals(3, viewModel.uiState.value.slotIds.size)
        viewModel.submit()
        assertTrue(viewModel.uiState.value.message!!.contains("T007"))
        assertEquals(OwnerWriteGate.SLOT_ATOMIC_SERVER_CONTRACT, viewModel.uiState.value.writeGate)
    }

    @Test fun invalidOrStaleFormIsRetainedAndSubmitIsNotEnabledByState() {
        val viewModel = vm()
        val input = SlotBlockInput("court-1", "20261029", "0700", "0600", "WALK_IN")
        viewModel.updateForm(input)
        viewModel.submit()
        assertEquals(input, viewModel.uiState.value.form)
        assertTrue(viewModel.uiState.value.error != null)
        viewModel.markStale()
        assertTrue(viewModel.uiState.value.isStale)
    }
}

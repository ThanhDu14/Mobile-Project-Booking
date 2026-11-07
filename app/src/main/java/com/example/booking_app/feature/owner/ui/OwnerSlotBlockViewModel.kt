package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.booking_app.feature.owner.domain.OwnerValidation
import com.example.booking_app.feature.owner.domain.OwnerWriteGate
import com.example.booking_app.feature.owner.domain.SlotBlockInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OwnerSlotBlockUiState(
    val form: SlotBlockInput,
    val slotIds: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val isStale: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val writeGate: OwnerWriteGate = OwnerWriteGate.SLOT_ATOMIC_SERVER_CONTRACT
)

/** UI-only preview until T007 provides an atomic server lock contract. */
class OwnerSlotBlockViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(
        OwnerSlotBlockUiState(SlotBlockInput(savedStateHandle["courtId"] ?: "", "", "", "", "MAINTENANCE"))
    )
    val uiState: StateFlow<OwnerSlotBlockUiState> = _uiState.asStateFlow()

    fun updateForm(form: SlotBlockInput) { _uiState.value = _uiState.value.copy(form = form, error = null, message = null) }
    fun setOffline(value: Boolean) { _uiState.value = _uiState.value.copy(isOffline = value) }
    fun markStale() { _uiState.value = _uiState.value.copy(isStale = true) }
    fun retryAfterRefresh() { _uiState.value = _uiState.value.copy(isStale = false, error = null) }

    fun preview() {
        val form = _uiState.value.form
        val errors = OwnerValidation.slotBlock(form)
        _uiState.value = if (errors.isNotEmpty()) {
            _uiState.value.copy(slotIds = emptyList(), error = errors.first().message)
        } else {
            _uiState.value.copy(slotIds = OwnerValidation.slotIds(form), error = null)
        }
    }

    fun submit() {
        if (_uiState.value.isSubmitting) return
        preview()
        if (_uiState.value.error != null) return
        _uiState.value = _uiState.value.copy(message = "Chưa thể khóa giờ: chờ hợp đồng server T007", isSubmitting = false, writeGate = OwnerWriteGate.SLOT_ATOMIC_SERVER_CONTRACT)
    }
}

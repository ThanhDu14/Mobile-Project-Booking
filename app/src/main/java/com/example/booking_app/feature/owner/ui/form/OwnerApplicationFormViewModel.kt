package com.example.booking_app.feature.owner.ui.form

import androidx.lifecycle.ViewModel
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationValidation
import com.example.booking_app.feature.owner.domain.OwnerApplicationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

data class OwnerApplicationFormUiState(
    val draft: OwnerApplicationDraft? = null,
    val validation: OwnerApplicationValidation? = null,
    val applicationId: String? = null,
    val rejectReason: String? = null,
)

sealed interface OwnerApplicationFormEvent {
    data object NavigateToReview : OwnerApplicationFormEvent
}

/** Holds applicant input and validation feedback; it performs no persistence or submission. */
@HiltViewModel
class OwnerApplicationFormViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerApplicationFormUiState())
    val uiState: StateFlow<OwnerApplicationFormUiState> = _uiState.asStateFlow()
    private val eventChannel = Channel<OwnerApplicationFormEvent>(Channel.BUFFERED)
    val events: Flow<OwnerApplicationFormEvent> = eventChannel.receiveAsFlow()

    fun onDraftChanged(draft: OwnerApplicationDraft) {
        _uiState.value = _uiState.value.copy(draft = draft, validation = null)
    }

    fun beginRejectedEdit(applicationId: String, rejectReason: String?, draft: OwnerApplicationDraft) {
        _uiState.value = OwnerApplicationFormUiState(
            draft = draft,
            applicationId = applicationId,
            rejectReason = rejectReason,
        )
    }

    fun beginNewApplication() {
        val currentDraft = _uiState.value.draft
        _uiState.value = _uiState.value.copy(
            draft = currentDraft?.copy(
                venue = currentDraft.venue.copy(photoPaths = emptyList()),
                documentPaths = emptyList(),
            ),
            applicationId = null,
            rejectReason = null,
        )
    }

    fun validateDraft(): OwnerApplicationValidation? {
        val draft = _uiState.value.draft ?: return null
        val validation = OwnerApplicationValidator.validate(draft)
        _uiState.value = _uiState.value.copy(validation = validation)
        return validation
    }

    /** Validates the locally retained draft before the UI may move to its review screen. */
    fun continueToReview(): OwnerApplicationValidation? {
        val validation = validateDraft() ?: return null
        if (validation.isValid) eventChannel.trySend(OwnerApplicationFormEvent.NavigateToReview)
        return validation
    }
}

package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.OwnerErrorClassifier
import com.example.booking_app.feature.owner.domain.OwnerErrorKind
import com.example.booking_app.feature.owner.domain.CourtInput
import com.example.booking_app.feature.owner.domain.OwnerValidation
import com.example.booking_app.feature.owner.domain.VenueInput
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerVenueListUiState(
    val isLoading: Boolean = false,
    val venues: List<OwnerVenue> = emptyList(),
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val errorKind: OwnerErrorKind? = null,
    val isFresh: Boolean = false
) {
    val isEmpty get() = !isLoading && error == null && venues.isEmpty()
}

data class OwnerVenueDetailUiState(
    val isLoading: Boolean = false,
    val venue: OwnerVenue? = null,
    val courts: List<OwnerCourt> = emptyList(),
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val draft: VenueEditableFields? = null,
    val errorKind: OwnerErrorKind? = null,
    val isFresh: Boolean = false
)

@HiltViewModel
class OwnerVenueListViewModel @Inject constructor(
    private val repository: OwnerVenueRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerVenueListUiState())
    val uiState: StateFlow<OwnerVenueListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, isFresh = false)
        repository.getOwnedVenues().onSuccess { venues ->
            _uiState.value = _uiState.value.copy(isLoading = false, venues = venues, error = null, errorKind = null, isFresh = true)
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Không tải được danh sách cơ sở", errorKind = OwnerErrorClassifier.kind(error), isFresh = false)
        }
    }

    fun hide(venueId: String) = submit { repository.hideVenue(venueId) }

    fun toggleRecurring(venue: OwnerVenue) = submit {
        repository.setAcceptsRecurring(venue.id, !venue.acceptsRecurring)
    }

    private fun submit(action: suspend () -> Result<Unit>) = viewModelScope.launch {
        if (_uiState.value.isSubmitting) return@launch
        _uiState.value = _uiState.value.copy(isSubmitting = true, message = null)
        action().onSuccess {
            _uiState.value = _uiState.value.copy(isSubmitting = false, message = "Đã lưu thay đổi")
            load()
        }.onFailure {
            _uiState.value = _uiState.value.copy(isSubmitting = false, message = it.message ?: "Không thể lưu thay đổi")
            if (OwnerErrorClassifier.kind(it) == OwnerErrorKind.CONFLICT) load()
        }
    }
}

@HiltViewModel
class OwnerVenueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OwnerVenueRepository
) : ViewModel() {
    private val venueId: String = checkNotNull(savedStateHandle["venueId"])
    private val _uiState = MutableStateFlow(OwnerVenueDetailUiState())
    val uiState: StateFlow<OwnerVenueDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, isFresh = false)
        val venue = repository.getVenue(venueId)
        val courts = repository.getCourts(venueId)
        if (venue.isSuccess && courts.isSuccess) {
            _uiState.value = _uiState.value.copy(isLoading = false, venue = venue.getOrThrow(), courts = courts.getOrThrow(), error = null, errorKind = null, isFresh = true)
        } else {
            val failure = venue.exceptionOrNull() ?: courts.exceptionOrNull()
            _uiState.value = _uiState.value.copy(isLoading = false, error = failure?.message ?: "Không tải được dữ liệu", errorKind = failure?.let(OwnerErrorClassifier::kind), isFresh = false)
        }
    }

    fun save(fields: VenueEditableFields) = viewModelScope.launch {
        if (_uiState.value.isSubmitting) return@launch
        val validationErrors = OwnerValidation.venue(VenueInput(fields.name, fields.address, fields.districtId, fields.location, fields.openTime, fields.closeTime, fields.phone, fields.amenityIds, fields.photoPaths, fields.courtCount, fields.acceptsRecurring))
        if (validationErrors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(message = validationErrors.first().message)
            return@launch
        }
        _uiState.value = _uiState.value.copy(isSubmitting = true, message = null, draft = fields)
        repository.updateVenue(venueId, fields).onSuccess {
            _uiState.value = _uiState.value.copy(isSubmitting = false, message = "Đã cập nhật cơ sở")
            load()
        }.onFailure {
            _uiState.value = _uiState.value.copy(isSubmitting = false, message = it.message ?: "Không thể cập nhật")
            if (OwnerErrorClassifier.kind(it) == OwnerErrorKind.CONFLICT) load()
        }
    }

    fun addCourt(fields: CourtFields) {
        if (!validateCourt(fields)) return
        courtAction { repository.createCourt(venueId, fields).map { Unit } }
    }
    fun updateCourt(courtId: String, fields: CourtFields) {
        if (!validateCourt(fields)) return
        courtAction { repository.updateCourt(venueId, courtId, fields) }
    }
    fun deactivateCourt(courtId: String) = courtAction { repository.deactivateCourt(venueId, courtId) }
    fun moveCourt(courtId: String, direction: Int) = courtAction {
        val courts = repository.getCourts(venueId).getOrThrow().sortedBy { it.sortOrder }
        val index = courts.indexOfFirst { it.id == courtId }
        val target = index + direction
        if (index < 0 || target !in courts.indices) return@courtAction Result.success(Unit)
        val current = courts[index]
        val other = courts[target]
        repository.updateCourt(venueId, current.id, CourtFields(current.name, current.surfaceType, current.isActive, other.sortOrder))
            .getOrThrow()
        repository.updateCourt(venueId, other.id, CourtFields(other.name, other.surfaceType, other.isActive, current.sortOrder))
    }

    private fun courtAction(action: suspend () -> Result<Unit>) = viewModelScope.launch {
        if (_uiState.value.isSubmitting) return@launch
        _uiState.value = _uiState.value.copy(isSubmitting = true, message = null)
        action().onSuccess {
            _uiState.value = _uiState.value.copy(isSubmitting = false, message = "Đã lưu sân")
            load()
        }.onFailure {
            _uiState.value = _uiState.value.copy(isSubmitting = false, message = it.message ?: "Không thể lưu sân")
            if (OwnerErrorClassifier.kind(it) == OwnerErrorKind.CONFLICT) load()
        }
    }

    private fun validateCourt(fields: CourtFields): Boolean {
        val errors = OwnerValidation.court(CourtInput(fields.name, fields.surfaceType, fields.sortOrder))
        if (errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(message = errors.first().message)
            return false
        }
        return true
    }
}

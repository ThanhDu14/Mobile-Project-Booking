package com.example.booking_app.feature.owner.ui.dropin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInDraft
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInDraftValidation
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRegistration
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRepository
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInSession
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OwnerDropInUiState(
    val venues: List<OwnerVenue> = emptyList(),
    val courts: List<OwnerCourt> = emptyList(),
    val sessions: List<OwnerDropInSession> = emptyList(),
    val roster: List<OwnerDropInRegistration> = emptyList(),
    val selectedVenueId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
    val validationErrors: List<String> = emptyList(),
    val completedMessage: String? = null,
)

@HiltViewModel
class OwnerDropInViewModel @Inject constructor(
    private val venueRepository: OwnerVenueRepository,
    private val dropInRepository: OwnerDropInRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerDropInUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private var venuesJob: Job? = null
    private var sessionJob: Job? = null
    private var courtJob: Job? = null

    init { loadVenues() }

    fun refresh() {
        _uiState.value.selectedVenueId?.let(::selectVenue) ?: loadVenues()
    }

    fun selectVenue(venueId: String) {
        if (venueId != _uiState.value.selectedVenueId) {
            _uiState.value = _uiState.value.copy(selectedVenueId = venueId, sessions = emptyList(), courts = emptyList())
        }
        loadSessions(venueId)
        loadCourts(venueId)
    }

    fun create(draft: OwnerDropInDraft) {
        val errors = OwnerDropInDraftValidation.validate(draft)
        if (errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(validationErrors = errors, error = null, completedMessage = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, completedMessage = null)
            dropInRepository.createSession(draft)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSaving = false, validationErrors = emptyList(), completedMessage = "Đã tạo buổi")
                    refresh()
                }
                .onFailure { failure ->
                    _uiState.value = _uiState.value.copy(isSaving = false, error = failure.message ?: "Không tạo được buổi")
                }
        }
    }

    fun loadRoster(venueId: String, sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            dropInRepository.getRoster(venueId, sessionId)
                .onSuccess { roster -> _uiState.value = _uiState.value.copy(roster = roster, isLoading = false, isEmpty = roster.isEmpty()) }
                .onFailure { failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = failure.message ?: "Không tải được danh sách đăng ký") }
        }
    }

    fun checkIn(venueId: String, sessionId: String, credential: String) {
        if (credential.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Nhập hoặc quét mã đăng ký.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            dropInRepository.checkIn(venueId, sessionId, credential.trim())
                .onSuccess { _uiState.value = _uiState.value.copy(isSaving = false, completedMessage = "Đã check-in") }
                .onFailure { failure -> _uiState.value = _uiState.value.copy(isSaving = false, error = failure.message ?: "Không check-in được") }
        }
    }

    private fun loadVenues() {
        venuesJob?.cancel()
        venuesJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            venueRepository.getOwnedVenues()
                .onSuccess { venues ->
                    val current = _uiState.value.selectedVenueId?.takeIf { id -> venues.any { it.id == id } }
                        ?: venues.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(venues = venues, selectedVenueId = current, isLoading = false, isEmpty = venues.isEmpty())
                    current?.let(::selectVenue)
                }
                .onFailure { failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = failure.message ?: "Không tải được cơ sở", isEmpty = false) }
        }
    }

    private fun loadCourts(venueId: String) {
        courtJob?.cancel()
        courtJob = viewModelScope.launch {
            venueRepository.getCourts(venueId)
                .onSuccess { courts -> _uiState.value = _uiState.value.copy(courts = courts.filter(OwnerCourt::isActive)) }
                .onFailure { failure -> _uiState.value = _uiState.value.copy(error = failure.message ?: "Không tải được danh sách sân") }
        }
    }

    private fun loadSessions(venueId: String) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, sessions = emptyList())
            try {
                dropInRepository.observeSessions(venueId).collect { sessions ->
                    _uiState.value = _uiState.value.copy(sessions = sessions, isLoading = false, error = null, isEmpty = sessions.isEmpty())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = failure.message ?: "Không tải được danh sách buổi", isEmpty = false)
            }
        }
    }
}

package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.model.District
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationFieldError
import com.example.booking_app.feature.owner.domain.OwnerApplicationSnapshot
import com.example.booking_app.feature.owner.domain.OwnerApplicationState
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatus
import com.example.booking_app.feature.owner.domain.OwnerApplicationValidation
import com.example.booking_app.feature.owner.domain.OwnerOnboardingRepository
import com.example.booking_app.feature.owner.domain.OwnerVenueDraft
import com.google.firebase.firestore.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class OwnerApplicationUiState(
    val loading: Boolean = true,
    val status: OwnerApplicationStatus? = null,
    val applicationId: String = UUID.randomUUID().toString(),
    val draft: OwnerApplicationDraft = emptyDraft(),
    val districts: List<District> = emptyList(),
    val errors: List<OwnerApplicationFieldError> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
) {
    companion object {
        fun emptyDraft() = OwnerApplicationDraft(
            venue = OwnerVenueDraft("", "", "", null, "", null, "", "", emptyList()),
            representativeName = "",
            representativePhone = "",
            documentPaths = emptyList(),
        )
    }
}

@HiltViewModel
class OwnerApplicationViewModel @Inject constructor(
    private val repository: OwnerOnboardingRepository,
    private val districtRepository: DistrictRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerApplicationUiState())
    val uiState: StateFlow<OwnerApplicationUiState> = _uiState.asStateFlow()
    private var initialized = false
    private var originalPaths: Set<String> = emptySet()

    fun initialize(resubmit: Boolean = false, forceNew: Boolean = false) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val status = repository.refreshStatus().getOrNull()
            val districts = runCatching { districtRepository.activeDistricts() }.getOrDefault(emptyList())
            var applicationId = UUID.randomUUID().toString()
            var draft = OwnerApplicationUiState.emptyDraft()
            when {
                status?.ownerStatus == OwnerApplicationState.REJECTED && status.application != null -> {
                    applicationId = status.application.applicationId
                    draft = status.application.draft
                    originalPaths = draft.venue.photoPaths.toSet() + draft.documentPaths
                }
                status?.ownerStatus == OwnerApplicationState.NONE && !forceNew -> {
                    val local = repository.loadLocalDraft().getOrNull()
                    if (local != null && local.first != status.application?.applicationId) {
                        applicationId = local.first
                        draft = local.second
                    }
                }
                status?.ownerStatus == OwnerApplicationState.PENDING -> Unit
                resubmit -> _uiState.update { it.copy(message = "Hồ sơ không còn ở trạng thái bị từ chối. Hãy làm mới.") }
            }
            _uiState.update {
                it.copy(loading = false, status = status, applicationId = applicationId, draft = draft, districts = districts)
            }
            persistDraft()
        }
    }

    fun refreshStatus() = viewModelScope.launch {
        _uiState.update { it.copy(loading = true, message = null) }
        repository.refreshStatus().onSuccess { status ->
            _uiState.update { it.copy(loading = false, status = status) }
        }.onFailure { error ->
            _uiState.update { it.copy(loading = false, message = error.message ?: "Không thể làm mới trạng thái") }
        }
    }

    fun update(field: String, value: String) {
        _uiState.update { state ->
            val v = state.draft.venue
            val venue = when (field) {
                "venueName" -> v.copy(name = value)
                "address" -> v.copy(address = value)
                "venuePhone" -> v.copy(phone = value)
                "courtCount" -> v.copy(courtCount = value.toIntOrNull())
                "openTime" -> v.copy(openTime = value)
                "closeTime" -> v.copy(closeTime = value)
                "representativeName" -> v.copy(name = v.name)
                else -> v
            }
            val draft = when (field) {
                "representativeName" -> state.draft.copy(representativeName = value)
                "representativePhone" -> state.draft.copy(representativePhone = value)
                "districtId" -> state.draft.copy(venue = v.copy(districtId = value))
                else -> state.draft.copy(venue = venue)
            }
            state.copy(draft = draft, errors = state.errors.filterNot { it.field == field })
        }
        persistDraft()
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        _uiState.update { state -> state.copy(draft = state.draft.copy(venue = state.draft.venue.copy(location = GeoPoint(latitude, longitude)))) }
        persistDraft()
    }

    fun addAttachment(fileName: String, mime: String, bytes: ByteArray, document: Boolean) = viewModelScope.launch {
        val current = _uiState.value
        if (current.busy) return@launch
        if (!document && current.draft.venue.photoPaths.size >= 5) {
            _uiState.update { it.copy(message = "Bạn có thể tải tối đa 5 ảnh cơ sở") }
            return@launch
        }
        val validation = OwnerApplicationValidation.validateEvidence(com.example.booking_app.feature.owner.domain.OwnerEvidenceFile(mime, bytes.size.toLong()))
        if (validation.isNotEmpty()) {
            _uiState.update { it.copy(errors = validation, message = validation.first().message) }
            return@launch
        }
        _uiState.update { it.copy(busy = true, message = null) }
        repository.upload(fileName, mime, bytes, current.applicationId).onSuccess { upload ->
            _uiState.update { state ->
                val draft = if (document) state.draft.copy(documentPaths = state.draft.documentPaths + upload.path)
                else state.draft.copy(venue = state.draft.venue.copy(photoPaths = state.draft.venue.photoPaths + upload.path))
                state.copy(draft = draft, busy = false, errors = state.errors.filterNot { it.field == "evidence" })
            }
            persistDraft()
        }.onFailure { error -> _uiState.update { it.copy(busy = false, message = error.message ?: "Tải tệp thất bại. Hãy thử lại.") } }
    }

    fun removeAttachment(path: String, document: Boolean) {
        _uiState.update { state ->
            val draft = if (document) state.draft.copy(documentPaths = state.draft.documentPaths - path)
            else state.draft.copy(venue = state.draft.venue.copy(photoPaths = state.draft.venue.photoPaths - path))
            state.copy(draft = draft)
        }
        if (path !in originalPaths) viewModelScope.launch { repository.deleteStaged(path) }
        persistDraft()
    }

    fun validate(): Boolean {
        val errors = OwnerApplicationValidation.validate(_uiState.value.draft)
        _uiState.update { it.copy(errors = errors, message = errors.firstOrNull()?.message) }
        return errors.isEmpty()
    }

    fun submit() = viewModelScope.launch {
        val current = _uiState.value
        if (!validate()) return@launch
        _uiState.update { it.copy(busy = true, message = null) }
        repository.submit(current.applicationId, current.draft).onSuccess { status ->
            val kept = current.draft.venue.photoPaths.toSet() + current.draft.documentPaths
            (originalPaths - kept).forEach { repository.deleteStaged(it) }
            originalPaths = kept
            _uiState.update { it.copy(busy = false, status = status) }
        }.onFailure { error ->
            // On an ambiguous response, read authoritative state before offering another submit.
            val latest = repository.refreshStatus().getOrNull()
            _uiState.update { it.copy(busy = false, status = latest ?: it.status, message = error.message ?: "Chưa xác định được kết quả. Hãy làm mới trạng thái trước khi gửi lại.") }
            persistDraft()
        }
    }

    private fun persistDraft() {
        val state = _uiState.value
        if (state.loading) return
        viewModelScope.launch { repository.saveLocalDraft(state.applicationId, state.draft) }
    }
}

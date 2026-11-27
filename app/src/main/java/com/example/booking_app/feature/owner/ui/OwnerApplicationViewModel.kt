package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.model.District
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationPurpose
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

data class OwnerPendingUpload(
    val id: String,
    val fileName: String,
    val document: Boolean,
    val failed: Boolean = false,
    val error: String? = null,
)

data class OwnerApplicationUiState(
    val additionalVenue: Boolean = false,
    val loading: Boolean = true,
    val status: OwnerApplicationStatus? = null,
    val statusFresh: Boolean = false,
    val applicationId: String = UUID.randomUUID().toString(),
    val draft: OwnerApplicationDraft = emptyDraft(),
    val districts: List<District> = emptyList(),
    val errors: List<OwnerApplicationFieldError> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
    val uploads: List<OwnerPendingUpload> = emptyList(),
) {
    val applicationState: OwnerApplicationState?
        get() = if (additionalVenue) status?.application?.status ?: status?.let { OwnerApplicationState.NONE } else status?.ownerStatus
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
    private val sessionManager: com.example.booking_app.core.session.SessionManager? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerApplicationUiState())
    val uiState: StateFlow<OwnerApplicationUiState> = _uiState.asStateFlow()
    private var initialized = false
    private var originalPaths: Set<String> = emptySet()
    private val retryableUploads = mutableMapOf<String, PendingUploadBytes>()

    private var purpose = OwnerApplicationPurpose.OWNER_ONBOARDING
    private var contextId: String? = null
    private var editingRejected = false

    fun initialize(resubmit: Boolean = false, forceNew: Boolean = false, additionalVenue: Boolean = false, applicationId: String? = null) {
        if (initialized && !forceNew && (applicationId == null || contextId == applicationId || _uiState.value.applicationId == applicationId)) {
            if (resubmit) restoreRejected()
            return
        }
        initialized = true
        purpose = if (additionalVenue) OwnerApplicationPurpose.VENUE_ADDITION else OwnerApplicationPurpose.OWNER_ONBOARDING
        contextId = applicationId
        originalPaths = emptySet()
        editingRejected = false
        retryableUploads.clear()
        _uiState.value = OwnerApplicationUiState(additionalVenue = additionalVenue)
        viewModelScope.launch {
            val statusResult = currentStatus()
            val status = statusResult.getOrNull()
            val districtsResult = loadDistricts()
            var draftId = applicationId ?: UUID.randomUUID().toString()
            var draft = OwnerApplicationUiState.emptyDraft()
            when {
                (if (additionalVenue) status?.application?.status else status?.ownerStatus) == OwnerApplicationState.REJECTED && status?.application != null -> {
                    draftId = status.application.applicationId
                    draft = status.application.draft
                    originalPaths = draft.venue.photoPaths.toSet() + draft.documentPaths
                    editingRejected = true
                }
                additionalVenue && status?.application == null || status?.ownerStatus == OwnerApplicationState.NONE -> {
                    val local = repository.loadDraft(purpose).getOrNull()
                    if (local != null && (applicationId == null || applicationId == local.first)) {
                        // A locally saved ID must not be reused once its lifecycle already exists
                        // on the server (for example, an approved owner was later revoked).
                        val existing = if (additionalVenue) repository.applicationStatus(purpose, local.first).getOrNull()?.application else status?.application
                        draftId = if (local.first == existing?.applicationId) {
                            UUID.randomUUID().toString()
                        } else {
                            local.first
                        }
                        if (!additionalVenue || local.first != existing?.applicationId) draft = local.second
                    }
                }
                status?.ownerStatus == OwnerApplicationState.PENDING -> Unit
                resubmit -> _uiState.update { it.copy(message = "Hồ sơ không còn ở trạng thái bị từ chối. Hãy làm mới.") }
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    status = status,
                    statusFresh = statusResult.isSuccess,
                    applicationId = draftId,
                    draft = draft,
                    districts = districtsResult.getOrDefault(emptyList()),
                    message = if (statusResult.isFailure) {
                        statusResult.exceptionOrNull()?.message ?: "Không thể tải trạng thái. Chạm để thử lại."
                    } else districtsResult.exceptionOrNull()?.message,
                )
            }
            if (additionalVenue) contextId = draftId
            persistDraft()
        }
    }

    private fun restoreRejected() {
        if (editingRejected) return
        val app = _uiState.value.status?.application ?: return
        if (app.status == OwnerApplicationState.REJECTED) {
            editingRejected = true
            originalPaths = app.draft.venue.photoPaths.toSet() + app.draft.documentPaths
            _uiState.update { it.copy(applicationId = app.applicationId, draft = app.draft) }
        }
    }

    private suspend fun currentStatus() = repository.applicationStatus(purpose, contextId)

    suspend fun refreshSession() { sessionManager?.refresh(forceTokenRefresh = true) }

    fun refreshStatus() = viewModelScope.launch {
        _uiState.update { it.copy(loading = true, statusFresh = false, message = null) }
        val districtsResult = loadDistricts()
        currentStatus().onSuccess { status ->
            _uiState.update { it.copy(loading = false, status = status, statusFresh = true,
                districts = districtsResult.getOrDefault(it.districts), message = districtsResult.exceptionOrNull()?.message) }
        }.onFailure { error ->
            _uiState.update { it.copy(loading = false, statusFresh = false, message = error.message ?: "Không thể làm mới trạng thái") }
        }
    }

    private suspend fun loadDistricts(): Result<List<District>> {
        return try {
            Result.success(districtRepository.activeDistricts().also { require(it.isNotEmpty()) })
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Result.failure(IllegalStateException("Không tải được quận/huyện. Chạm vào quận/huyện để thử lại.", error))
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
        val id = UUID.randomUUID().toString()
        retryableUploads[id] = PendingUploadBytes(fileName, mime, bytes.copyOf(), document)
        uploadPendingFile(id)
    }

    fun retryAttachment(id: String) = viewModelScope.launch {
        if (_uiState.value.busy || id !in retryableUploads) return@launch
        uploadPendingFile(id)
    }

    fun discardFailedAttachment(id: String) {
        if (_uiState.value.uploads.none { it.id == id && it.failed }) return
        retryableUploads.remove(id)
        _uiState.update { state -> state.copy(uploads = state.uploads.filterNot { it.id == id }) }
    }

    private suspend fun uploadPendingFile(id: String) {
        val pending = retryableUploads[id] ?: return
        val applicationId = _uiState.value.applicationId
        _uiState.update { state ->
            state.copy(
                busy = true,
                message = null,
                uploads = state.uploads.filterNot { it.id == id } + OwnerPendingUpload(id, pending.fileName, pending.document),
            )
        }
        repository.uploadApplication(pending.fileName, pending.mime, pending.bytes, applicationId, purpose).onSuccess { upload ->
            retryableUploads.remove(id)
            _uiState.update { state ->
                val draft = if (pending.document) state.draft.copy(documentPaths = state.draft.documentPaths + upload.path)
                else state.draft.copy(venue = state.draft.venue.copy(photoPaths = state.draft.venue.photoPaths + upload.path))
                state.copy(
                    draft = draft,
                    busy = false,
                    uploads = state.uploads.filterNot { it.id == id },
                    errors = state.errors.filterNot { it.field == "evidence" },
                )
            }
            persistDraft()
        }.onFailure { error ->
            val message = error.message ?: "Tải tệp thất bại. Hãy thử lại."
            _uiState.update { state ->
                state.copy(
                    busy = false,
                    message = message,
                    uploads = state.uploads.map { if (it.id == id) it.copy(failed = true, error = message) else it },
                )
            }
        }
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
        if (_uiState.value.busy || _uiState.value.loading) return@launch
        val current = _uiState.value
        if (!validate()) return@launch
        _uiState.update { it.copy(loading = true, statusFresh = false, message = null) }
        val latestResult = currentStatus()
        val latest = latestResult.getOrNull()
        if (latest == null) {
            _uiState.update { it.copy(loading = false, statusFresh = false, message = "Không thể xác minh trạng thái hồ sơ. Hãy thử lại trước khi nộp.") }
            return@launch
        }
        val lifecycleAllowed = if (current.additionalVenue) {
            latest.role == "OWNER" && latest.ownerStatus == OwnerApplicationState.APPROVED &&
                (latest.application == null || latest.application.applicationId == current.applicationId && latest.application.status == OwnerApplicationState.REJECTED)
        } else when (latest.ownerStatus) {
            OwnerApplicationState.NONE -> latest.role == "PLAYER" && latest.application?.applicationId != current.applicationId
            OwnerApplicationState.REJECTED -> latest.role == "PLAYER" && latest.application?.applicationId == current.applicationId
            OwnerApplicationState.PENDING, OwnerApplicationState.APPROVED -> false
        }
        if (!lifecycleAllowed) {
            val message = when (latest.ownerStatus) {
                OwnerApplicationState.PENDING -> "Hồ sơ đang chờ duyệt và không thể sửa hoặc nộp thêm."
                OwnerApplicationState.APPROVED -> "Tài khoản đã được duyệt chủ sân."
                OwnerApplicationState.REJECTED -> "Hồ sơ đã thay đổi. Hãy làm mới trước khi nộp lại."
                OwnerApplicationState.NONE -> "Trạng thái tài khoản chưa hợp lệ để nộp hồ sơ."
            }
            _uiState.update { it.copy(loading = false, status = latest, statusFresh = true, message = message) }
            return@launch
        }
        _uiState.update { it.copy(loading = false, status = latest, statusFresh = true) }
        val fresh = _uiState.value
        _uiState.update { it.copy(busy = true, message = null) }
        repository.submitApplication(fresh.applicationId, fresh.draft, purpose).onSuccess { status ->
            val kept = fresh.draft.venue.photoPaths.toSet() + fresh.draft.documentPaths
            (originalPaths - kept).forEach { repository.deleteStaged(it) }
            originalPaths = kept
            _uiState.update { it.copy(busy = false, status = status, statusFresh = true) }
        }.onFailure { error ->
            // On an ambiguous response, read authoritative state before offering another submit.
            val latest = currentStatus().getOrNull()
            _uiState.update {
                it.copy(
                    busy = false,
                    status = latest ?: it.status,
                    statusFresh = latest != null,
                    message = error.message ?: "Chưa xác định được kết quả. Hãy làm mới trạng thái trước khi gửi lại.",
                )
            }
            persistDraft()
        }
    }

    suspend fun signedDocumentUrl(applicationId: String, path: String): Result<String> =
        repository.signedDocumentUrl(applicationId, path)

    suspend fun downloadDocument(applicationId: String, path: String): Result<ByteArray> =
        repository.downloadDocument(applicationId, path)

    private fun persistDraft() {
        val state = _uiState.value
        if (state.loading) return
        val draftPurpose = purpose
        viewModelScope.launch { repository.saveDraft(state.applicationId, state.draft, draftPurpose) }
    }

    private data class PendingUploadBytes(
        val fileName: String,
        val mime: String,
        val bytes: ByteArray,
        val document: Boolean,
    )
}

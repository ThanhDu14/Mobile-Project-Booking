package com.example.booking_app.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.admin.domain.model.AdminOwnerApplication
import com.example.booking_app.feature.admin.domain.model.ApplicationReviewDecision
import com.example.booking_app.feature.admin.domain.model.ReviewAction
import com.example.booking_app.feature.admin.domain.repository.AdminApplicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

sealed interface AdminQueueState {
    data object Loading : AdminQueueState
    data object Empty : AdminQueueState
    data class Content(val applications: List<AdminOwnerApplication>) : AdminQueueState
    data class Error(val message: String, val canRetry: Boolean = true) : AdminQueueState
}

data class AdminDetailUiState(
    val application: AdminOwnerApplication? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val rejectReasonInput: String = "",
    val documentUrls: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isAuthoritative: Boolean = false,
    val permissionDenied: Boolean = false,
)

@HiltViewModel
class AdminApplicationsViewModel @Inject constructor(
    private val repository: AdminApplicationRepository,
) : ViewModel() {

    private val _queueState = MutableStateFlow<AdminQueueState>(AdminQueueState.Loading)
    val queueState: StateFlow<AdminQueueState> = _queueState.asStateFlow()

    private val _detailState = MutableStateFlow(AdminDetailUiState())
    val detailState: StateFlow<AdminDetailUiState> = _detailState.asStateFlow()



    private var queueJob: Job? = null
    var queueStatus: String = "PENDING"; private set
    private var pageLimit = 30L

    fun selectStatus(status: String) {
        queueStatus = status
        pageLimit = 30
        loadQueue()
    }

    fun loadMore() {
        val content = _queueState.value as? AdminQueueState.Content ?: return
        if (content.applications.size.toLong() < pageLimit) return
        pageLimit += 30
        loadQueue()
    }

    fun stopQueue() { queueJob?.cancel() }

    fun loadQueue() {
        queueJob?.cancel()
        _queueState.value = AdminQueueState.Loading
        queueJob = viewModelScope.launch {
            repository.observeApplications(queueStatus, pageLimit)
                .catch { e ->
                    _queueState.value = AdminQueueState.Error(
                        message = e.localizedMessage ?: "Không thể tải danh sách hồ sơ",
                        canRetry = true,
                    )
                }
                .collect { list ->
                    _queueState.value = if (list.isEmpty()) {
                        AdminQueueState.Empty
                    } else {
                        AdminQueueState.Content(list)
                    }
                }
        }
    }

    fun selectApplication(application: AdminOwnerApplication) {
        _detailState.update {
            if (it.application?.id == application.id) it.copy(application = application) else AdminDetailUiState(
                application = application,
                isLoading = false,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun loadApplicationDetail(applicationId: String) {
        _detailState.update { it.copy(isLoading = true, isAuthoritative = false, permissionDenied = false, errorMessage = null) }
        viewModelScope.launch {
            repository.getApplication(applicationId)
                .onSuccess { app ->
                    if (_detailState.value.application?.id?.let { it != applicationId } == true) return@onSuccess
                    _detailState.update {
                        it.copy(
                            application = app,
                            isLoading = false,
                            isAuthoritative = true,
                        )
                    }
                }
                .onFailure { err ->
                    if (_detailState.value.application?.id?.let { it != applicationId } == true) return@onFailure
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            isAuthoritative = false,
                            permissionDenied = (err as? com.google.firebase.firestore.FirebaseFirestoreException)?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
                            errorMessage = err.localizedMessage ?: "Không thể tải chi tiết hồ sơ",
                        )
                    }
                }
        }
    }

    fun updateRejectReasonInput(input: String) {
        _detailState.update { it.copy(rejectReasonInput = input) }
    }

    fun loadDocumentSignedUrl(path: String, onReady: ((String) -> Unit)? = null) {
        val app = _detailState.value.application ?: return
        _detailState.update { it.copy(documentUrls = it.documentUrls - path) }
        viewModelScope.launch {
            repository.getSignedDocumentUrl(app.id, path)
                .onSuccess { url ->
                    if (_detailState.value.application?.id != app.id) return@onSuccess
                    _detailState.update {
                        it.copy(documentUrls = it.documentUrls + (path to url))
                    }
                    onReady?.invoke(url)
                }
                .onFailure { err ->
                    if (_detailState.value.application?.id != app.id) return@onFailure
                    _detailState.update {
                        it.copy(errorMessage = "Không thể tải tài liệu: ${err.localizedMessage}")
                    }
                }
        }
    }

    fun submitDecision(action: ReviewAction, onComplete: ((Boolean) -> Unit)? = null) {
        if (_detailState.value.isSubmitting) return
        val app = _detailState.value.application ?: return
        val reason = _detailState.value.rejectReasonInput.trim()
        val decision = ApplicationReviewDecision(
            applicationId = app.id,
            action = action,
            rejectReason = if (action == ReviewAction.REJECT) reason else null,
            expectedSubmittedAt = app.submittedAt.takeIf { it > 0 },
        )

        val validationErr = decision.validate()
        if (validationErr != null) {
            _detailState.update { it.copy(errorMessage = validationErr) }
            return
        }

        _detailState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.reviewApplication(decision)
                .onSuccess {
                    if (_detailState.value.application?.id != app.id) return@onSuccess
                    _detailState.update {
                        it.copy(
                            isSubmitting = false,
                            successMessage = if (action == ReviewAction.APPROVE) "Duyệt hồ sơ thành công" else "Đã từ chối hồ sơ",
                            rejectReasonInput = "",
                        )
                    }
                    onComplete?.invoke(true)
                }
                .onFailure { err ->
                    val latest = repository.getApplication(app.id).getOrNull()
                    if (_detailState.value.application?.id != app.id) return@onFailure
                    _detailState.update {
                        it.copy(
                            application = latest ?: it.application,
                            isAuthoritative = latest != null,
                            isSubmitting = false,
                            errorMessage = err.localizedMessage ?: "Thao tác thất bại. Vui lòng kiểm tra lại trạng thái hồ sơ.",
                        )
                    }
                    onComplete?.invoke(false)
                }
        }
    }

    fun clearMessages() {
        _detailState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun recoverClaims() {
        val state = _detailState.value
        val app = state.application ?: return
        if (state.isSubmitting || app.status !in setOf("APPROVED", "REJECTED")) return
        _detailState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.recoverClaims(app.id)
                .onSuccess { if (_detailState.value.application?.id == app.id) _detailState.update { it.copy(isSubmitting = false, successMessage = "Đã đồng bộ quyền theo trạng thái hiện tại") } }
                .onFailure { error -> if (_detailState.value.application?.id == app.id) _detailState.update { it.copy(isSubmitting = false, errorMessage = error.localizedMessage ?: "Chưa đồng bộ được quyền. Vui lòng thử lại.") } }
        }
    }
    init {
        loadQueue()
    }
}

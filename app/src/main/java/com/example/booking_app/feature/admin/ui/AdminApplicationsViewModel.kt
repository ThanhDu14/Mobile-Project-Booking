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
)

@HiltViewModel
class AdminApplicationsViewModel @Inject constructor(
    private val repository: AdminApplicationRepository,
) : ViewModel() {

    private val _queueState = MutableStateFlow<AdminQueueState>(AdminQueueState.Loading)
    val queueState: StateFlow<AdminQueueState> = _queueState.asStateFlow()

    private val _detailState = MutableStateFlow(AdminDetailUiState())
    val detailState: StateFlow<AdminDetailUiState> = _detailState.asStateFlow()

    init {
        loadQueue()
    }

    fun loadQueue() {
        _queueState.value = AdminQueueState.Loading
        viewModelScope.launch {
            repository.observePendingApplications()
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
            it.copy(
                application = application,
                isLoading = false,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun loadApplicationDetail(applicationId: String) {
        _detailState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.getApplication(applicationId)
                .onSuccess { app ->
                    _detailState.update {
                        it.copy(
                            application = app,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { err ->
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Không thể tải chi tiết hồ sơ",
                        )
                    }
                }
        }
    }

    fun updateRejectReasonInput(input: String) {
        _detailState.update { it.copy(rejectReasonInput = input) }
    }

    fun loadDocumentSignedUrl(path: String) {
        val app = _detailState.value.application ?: return
        viewModelScope.launch {
            repository.getSignedDocumentUrl(app.id, path)
                .onSuccess { url ->
                    _detailState.update {
                        it.copy(documentUrls = it.documentUrls + (path to url))
                    }
                }
                .onFailure { err ->
                    _detailState.update {
                        it.copy(errorMessage = "Không thể tải tài liệu: ${err.localizedMessage}")
                    }
                }
        }
    }

    fun submitDecision(action: ReviewAction, onComplete: ((Boolean) -> Unit)? = null) {
        val app = _detailState.value.application ?: return
        val reason = _detailState.value.rejectReasonInput.trim()
        val decision = ApplicationReviewDecision(
            applicationId = app.id,
            action = action,
            rejectReason = if (action == ReviewAction.REJECT) reason else null,
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
                    _detailState.update {
                        it.copy(
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
}

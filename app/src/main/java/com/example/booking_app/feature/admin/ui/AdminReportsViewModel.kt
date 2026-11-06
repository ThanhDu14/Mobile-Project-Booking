package com.example.booking_app.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.admin.domain.model.AdminReport
import com.example.booking_app.feature.admin.domain.model.ModerateReportDecision
import com.example.booking_app.feature.admin.domain.model.ReportAction
import com.example.booking_app.feature.admin.domain.repository.AdminReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AdminReportQueueState {
    data object Loading : AdminReportQueueState
    data object Empty : AdminReportQueueState
    data class Content(val reports: List<AdminReport>) : AdminReportQueueState
    data class Error(val message: String, val canRetry: Boolean = true) : AdminReportQueueState
}

data class AdminReportDetailUiState(
    val report: AdminReport? = null,
    val targetSummary: Map<String, Any?> = emptyMap(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class AdminReportsViewModel @Inject constructor(
    private val repository: AdminReportRepository,
) : ViewModel() {

    private val _queueState = MutableStateFlow<AdminReportQueueState>(AdminReportQueueState.Loading)
    val queueState: StateFlow<AdminReportQueueState> = _queueState.asStateFlow()

    private val _detailState = MutableStateFlow(AdminReportDetailUiState())
    val detailState: StateFlow<AdminReportDetailUiState> = _detailState.asStateFlow()

    init {
        loadReports()
    }

    fun loadReports() {
        _queueState.value = AdminReportQueueState.Loading
        viewModelScope.launch {
            repository.observeOpenReports()
                .catch { e ->
                    _queueState.value = AdminReportQueueState.Error(
                        message = e.localizedMessage ?: "Không thể tải danh sách báo cáo",
                        canRetry = true,
                    )
                }
                .collect { list ->
                    _queueState.value = if (list.isEmpty()) {
                        AdminReportQueueState.Empty
                    } else {
                        AdminReportQueueState.Content(list)
                    }
                }
        }
    }

    fun selectReport(report: AdminReport) {
        _detailState.update {
            it.copy(
                report = report,
                isLoading = false,
                errorMessage = null,
                successMessage = null,
            )
        }
        if (report.targetPath.isNotBlank()) {
            loadTargetSummary(report.targetPath)
        }
    }

    fun loadReportDetail(reportId: String) {
        _detailState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.getReport(reportId)
                .onSuccess { report ->
                    _detailState.update {
                        it.copy(report = report, isLoading = false)
                    }
                    if (report.targetPath.isNotBlank()) {
                        loadTargetSummary(report.targetPath)
                    }
                }
                .onFailure { err ->
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Không thể tải chi tiết báo cáo",
                        )
                    }
                }
        }
    }

    private fun loadTargetSummary(targetPath: String) {
        viewModelScope.launch {
            repository.getTargetSummary(targetPath)
                .onSuccess { data ->
                    _detailState.update { it.copy(targetSummary = data) }
                }
        }
    }

    fun submitModeration(action: ReportAction, onComplete: ((Boolean) -> Unit)? = null) {
        val currentReport = _detailState.value.report ?: return
        val decision = ModerateReportDecision(
            reportId = currentReport.id,
            action = action,
            targetType = currentReport.targetType,
        )

        val validationErr = decision.validate()
        if (validationErr != null) {
            _detailState.update { it.copy(errorMessage = validationErr) }
            onComplete?.invoke(false)
            return
        }

        _detailState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.moderateReport(decision)
                .onSuccess {
                    val msg = if (action == ReportAction.NONE) "Đã bỏ qua báo cáo" else "Đã xử lý vi phạm thành công"
                    _detailState.update {
                        it.copy(
                            isSubmitting = false,
                            successMessage = msg,
                        )
                    }
                    onComplete?.invoke(true)
                }
                .onFailure { err ->
                    _detailState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = err.localizedMessage ?: "Không thể xử lý báo cáo. Vui lòng thử lại.",
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

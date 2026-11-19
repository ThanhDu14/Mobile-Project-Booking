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
import kotlinx.coroutines.Job
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



    private var queueJob: Job? = null
    var closedTab = false; private set
    private var pageLimit = 30L

    fun selectStatus(closed: Boolean) { closedTab = closed; pageLimit = 30; loadReports() }
    fun loadMore() {
        val content = _queueState.value as? AdminReportQueueState.Content ?: return
        if (content.reports.size.toLong() < pageLimit) return
        pageLimit += 30
        loadReports()
    }
    fun stopQueue() { queueJob?.cancel() }

    fun loadReports() {
        queueJob?.cancel()
        _queueState.value = AdminReportQueueState.Loading
        queueJob = viewModelScope.launch {
            repository.observeReports(closedTab, pageLimit)
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
                targetSummary = emptyMap(),
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
                    if (_detailState.value.report?.id?.let { it != reportId } == true) return@onSuccess
                    _detailState.update {
                        it.copy(report = report, isLoading = false)
                    }
                    if (report.targetPath.isNotBlank()) {
                        loadTargetSummary(report.targetPath)
                    }
                }
                .onFailure { err ->
                    if (_detailState.value.report?.id?.let { it != reportId } == true) return@onFailure
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
                    if (_detailState.value.report?.targetPath == targetPath) _detailState.update { it.copy(targetSummary = data) }
                }
        }
    }

    fun submitModeration(action: ReportAction, onComplete: ((Boolean) -> Unit)? = null) {
        if (_detailState.value.isSubmitting) return
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
                    if (_detailState.value.report?.id != currentReport.id) return@onSuccess
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
                    if (_detailState.value.report?.id != currentReport.id) return@onFailure
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
    init {
        loadReports()
    }
}

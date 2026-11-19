package com.example.booking_app.feature.community.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.model.ReportReason
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.ReportPost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReportDetailError { REQUIRED, TOO_LONG }

data class ReportPostState(
    val selectedReason: ReportReason? = null,
    val detail: String = "",
    val reasonError: Boolean = false,
    val detailError: ReportDetailError? = null,
    val submitting: Boolean = false,
) {
    val showDetailField: Boolean get() = selectedReason == ReportReason.OTHER
}

sealed interface ReportPostEvent {
    data object Sent : ReportPostEvent
    data class ShowError(val error: CommunityError) : ReportPostEvent
}

/**
 * T068: báo cáo bài đăng vi phạm (FR-018; wireframe 09_community-chat_buoc3_bao-cao-bai-dang.png).
 * Chọn 1 trong 5 lý do; "Khác" bắt buộc mô tả ≤ 500 ký tự; lỗi mạng giữ nguyên lựa chọn và mô tả.
 */
@HiltViewModel
class ReportPostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportPost: ReportPost,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "Thiếu groupId" }
    val postId: String = checkNotNull(savedStateHandle[ARG_POST_ID]) { "Thiếu postId" }

    private val _state = MutableStateFlow(ReportPostState())
    val state: StateFlow<ReportPostState> = _state.asStateFlow()

    private val _events = Channel<ReportPostEvent>(Channel.BUFFERED)
    val events: Flow<ReportPostEvent> = _events.receiveAsFlow()

    fun onReasonSelected(reason: ReportReason) {
        _state.update { it.copy(selectedReason = reason, reasonError = false, detailError = null) }
    }

    fun onDetailChanged(value: String) {
        _state.update { it.copy(detail = value, detailError = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val reason = current.selectedReason
        if (reason == null) {
            _state.update { it.copy(reasonError = true) }
            return
        }
        val trimmedDetail = current.detail.trim()
        val detailError = when {
            reason == ReportReason.OTHER && trimmedDetail.isEmpty() -> ReportDetailError.REQUIRED
            trimmedDetail.length > MAX_DETAIL_LENGTH -> ReportDetailError.TOO_LONG
            else -> null
        }
        if (detailError != null) {
            _state.update { it.copy(detailError = detailError) }
            return
        }

        val detailToSend = trimmedDetail.takeIf { reason == ReportReason.OTHER && it.isNotEmpty() }
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = try {
                reportPost(groupId, postId, reason, detailToSend)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.fold(
                onSuccess = {
                    _events.trySend(ReportPostEvent.Sent)
                },
                onFailure = { err ->
                    _state.update { it.copy(submitting = false) }
                    _events.trySend(ReportPostEvent.ShowError(err.toCommunityError()))
                },
            )
        }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        const val ARG_POST_ID = "postId"
        const val MAX_DETAIL_LENGTH = 500
    }
}

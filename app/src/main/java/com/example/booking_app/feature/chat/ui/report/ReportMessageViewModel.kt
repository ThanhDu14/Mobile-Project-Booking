package com.example.booking_app.feature.chat.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.model.ChatMessageRules
import com.example.booking_app.feature.chat.domain.model.ReportReason
import com.example.booking_app.feature.chat.domain.toChatError
import com.example.booking_app.feature.chat.domain.usecase.ReportMessage
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

enum class ReportMessageDetailError { REQUIRED, TOO_LONG }

data class ReportMessageState(
    val selectedReason: ReportReason? = null,
    val detail: String = "",
    val reasonError: Boolean = false,
    val detailError: ReportMessageDetailError? = null,
    val submitting: Boolean = false,
) {
    val showDetailField: Boolean get() = selectedReason == ReportReason.OTHER
}

sealed interface ReportMessageEvent {
    data object Sent : ReportMessageEvent
    data class ShowError(val error: ChatError) : ReportMessageEvent
}

/**
 * Báo cáo tin nhắn vi phạm (FR-014, T052; cùng mô hình với `ReportPostViewModel` của spec 090).
 * Chọn 1 trong 5 lý do; "Khác" bắt buộc mô tả ≤ 500 ký tự; lỗi mạng giữ nguyên lựa chọn và mô tả.
 */
@HiltViewModel
class ReportMessageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportMessage: ReportMessage,
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle[ARG_CHAT_ID]) { "Thiếu chatId" }
    val messageId: String = checkNotNull(savedStateHandle[ARG_MESSAGE_ID]) { "Thiếu messageId" }

    private val _state = MutableStateFlow(ReportMessageState())
    val state: StateFlow<ReportMessageState> = _state.asStateFlow()

    private val _events = Channel<ReportMessageEvent>(Channel.BUFFERED)
    val events: Flow<ReportMessageEvent> = _events.receiveAsFlow()

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
        val detailError = if (reason == ReportReason.OTHER) {
            when {
                trimmedDetail.isEmpty() -> ReportMessageDetailError.REQUIRED
                ChatMessageRules.codePointLength(trimmedDetail) > MAX_DETAIL_LENGTH -> ReportMessageDetailError.TOO_LONG
                else -> null
            }
        } else {
            null
        }
        if (detailError != null) {
            _state.update { it.copy(detailError = detailError) }
            return
        }

        val detailToSend = trimmedDetail.takeIf { reason == ReportReason.OTHER && it.isNotEmpty() }
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = try {
                reportMessage(chatId, messageId, reason, detailToSend)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.fold(
                onSuccess = {
                    _events.trySend(ReportMessageEvent.Sent)
                },
                onFailure = { err ->
                    _state.update { it.copy(submitting = false) }
                    _events.trySend(ReportMessageEvent.ShowError(err.toChatError()))
                },
            )
        }
    }

    companion object {
        const val ARG_CHAT_ID = "chatId"
        const val ARG_MESSAGE_ID = "messageId"
        const val MAX_DETAIL_LENGTH = 500
    }
}

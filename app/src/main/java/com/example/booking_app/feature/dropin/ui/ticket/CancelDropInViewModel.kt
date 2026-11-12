package com.example.booking_app.feature.dropin.ui.ticket

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.dropin.domain.model.CancelQuote
import com.example.booking_app.feature.dropin.domain.model.CancelReason
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.usecase.CancelDropIn
import com.example.booking_app.feature.dropin.domain.usecase.QuoteCancel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface CancelDropInUiState {
    /** Đang hỏi server số tiền hoàn (`dryRun`). */
    data object Quoting : CancelDropInUiState

    /** Không lấy được số tiền hoàn (mất mạng, bận...): hiện lỗi và nút "Thử lại". */
    data class QuoteFailed(val error: Throwable) : CancelDropInUiState

    /** Đã có số tiền hoàn, chờ người chơi xác nhận. [error] là lỗi của lần xác nhận trước (vẫn ở lại sheet). */
    data class Quote(val quote: CancelQuote, val error: Throwable? = null) : CancelDropInUiState {
        /** Mất mạng hoặc hệ thống bận: bấm lại sẽ gửi cùng `requestId`. */
        val canRetry: Boolean get() = error is DropInError.Network || error is DropInError.Busy
    }

    data class Cancelling(val quote: CancelQuote) : CancelDropInUiState

    /** Đã hủy; [refundAmount] là số tiền thực sự được hoàn. */
    data class Done(val refundAmount: Long) : CancelDropInUiState
}

sealed interface CancelDropInEvent {
    /** Đóng sheet vì không thể hủy nữa (buổi đã bắt đầu, đã check-in, không còn lượt...). */
    data class Close(val error: Throwable) : CancelDropInEvent
}

/** Dòng chính của sheet theo kết quả tính tiền (FR-017). */
enum class CancelQuoteText {
    /** "Bạn được hoàn 100% (X đ)". */
    REFUND_FULL,

    /** "Không được hoàn tiền". */
    NO_REFUND,

    /** "Không mất phí" (lượt trả tại sân, chưa trả gì). */
    FREE,
    ;

    companion object {
        fun of(quote: CancelQuote): CancelQuoteText = when (quote.reason) {
            CancelReason.AT_VENUE -> FREE
            CancelReason.LATE -> NO_REFUND
            CancelReason.EARLY, CancelReason.SESSION_CHANGED -> if (quote.refundAmount > 0) REFUND_FULL else NO_REFUND
        }
    }
}

/**
 * Bottom sheet hủy đăng ký (US4, T055; contracts/ui-screens.md mục 2). Mở là hỏi số tiền hoàn bằng `dryRun`, xác nhận
 * thì hủy thật. `requestId` sinh một lần và giữ trong [SavedStateHandle]: mất mạng hoặc bận thì sheet ở lại, bấm lại
 * gửi cùng mã nên server không trả chỗ hai lần (FR-018).
 */
@HiltViewModel
class CancelDropInViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val quoteCancel: QuoteCancel,
    private val cancelDropIn: CancelDropIn,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID]) { "Thiếu sessionId" }

    private val requestId: String = savedStateHandle.get<String>(KEY_REQUEST_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_REQUEST_ID] = it }

    private val _uiState = MutableStateFlow<CancelDropInUiState>(CancelDropInUiState.Quoting)
    val uiState: StateFlow<CancelDropInUiState> = _uiState.asStateFlow()

    private val _events = Channel<CancelDropInEvent>(Channel.BUFFERED)
    val events: Flow<CancelDropInEvent> = _events.receiveAsFlow()

    init {
        loadQuote()
    }

    fun retryQuote() {
        if (_uiState.value is CancelDropInUiState.QuoteFailed) loadQuote()
    }

    private fun loadQuote() {
        _uiState.value = CancelDropInUiState.Quoting
        viewModelScope.launch {
            val result = safely { quoteCancel(sessionId, requestId) }
            result.fold(
                onSuccess = { _uiState.value = CancelDropInUiState.Quote(it) },
                onFailure = { error ->
                    if (isFinal(error)) {
                        _events.send(CancelDropInEvent.Close(error))
                    } else {
                        _uiState.value = CancelDropInUiState.QuoteFailed(error)
                    }
                },
            )
        }
    }

    /** Xác nhận hủy. Chỉ chạy khi đã có số tiền hoàn và không đang hủy. */
    fun confirm() {
        val state = _uiState.value as? CancelDropInUiState.Quote ?: return
        _uiState.value = CancelDropInUiState.Cancelling(state.quote)
        viewModelScope.launch {
            val result = safely { cancelDropIn(sessionId, requestId) }
            result.fold(
                onSuccess = { registration ->
                    _uiState.value = CancelDropInUiState.Done(registration.refundAmount ?: state.quote.refundAmount)
                },
                onFailure = { error ->
                    if (isFinal(error)) {
                        _events.send(CancelDropInEvent.Close(error))
                    } else {
                        // Mất mạng, bận, lỗi lạ: ở lại sheet, bấm lại dùng cùng requestId.
                        _uiState.value = CancelDropInUiState.Quote(state.quote, error)
                    }
                },
            )
        }
    }

    /** Lỗi mà thử lại cũng không hủy được: đóng sheet và báo ở màn hình vé. */
    private fun isFinal(error: Throwable): Boolean = when (error) {
        is DropInError.SessionStarted,
        is DropInError.NotCancellable,
        is DropInError.NotFound,
        is DropInError.SessionCancelled,
        is DropInError.Unauthenticated,
        is DropInError.AccountLocked,
        -> true
        else -> false
    }

    private suspend fun <T> safely(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        internal const val KEY_REQUEST_ID = "dropin_cancel_request_id"
    }
}

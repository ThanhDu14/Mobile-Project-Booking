package com.example.booking_app.feature.dropin.ui.waitlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistEndReason
import com.example.booking_app.feature.dropin.domain.model.WaitlistPosition
import com.example.booking_app.feature.dropin.domain.usecase.LeaveWaitlist
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Màn hình lượt chờ (spec 081 US1, US3, US4; contracts/ui-screens.md mục 2). */
sealed interface WaitlistEntryUiState {
    data object Loading : WaitlistEntryUiState

    /**
     * Đang chờ ở vị trí [position]/[total] (từ `session.waitlistTickets`). [amount] là số tiền sẽ ghi nhận khi được nhận,
     * chưa trừ gì (FR-002). [leaveError] là lỗi của lần xác nhận rời trước (mất mạng, bận): Fragment mở lại hộp thoại,
     * bấm lại gửi cùng `requestId`.
     */
    data class Content(
        val position: Int,
        val total: Int,
        val partySize: Int,
        val amount: Long,
        val paymentMethod: PaymentMethod,
        val session: DropInSession?,
        val leaveError: Throwable? = null,
    ) : WaitlistEntryUiState

    /** Đang gửi yêu cầu rời hàng chờ. */
    data class Leaving(val content: Content) : WaitlistEntryUiState

    /** Đã rời hàng chờ. */
    data object Left : WaitlistEntryUiState

    /**
     * Lượt chờ đã kết thúc mà không được nhận (US4, AC 4.2, 4.4): [reason] là `CLOSED`, `SESSION_CANCELLED`,
     * `AUTO_LEFT_OVERLAP`, `LEFT`; `null` nếu dữ liệu thiếu lý do.
     */
    data class Ended(val reason: WaitlistEndReason?) : WaitlistEntryUiState

    /** Không có lượt chờ nào ở buổi này. */
    data object Empty : WaitlistEntryUiState

    data class Error(val cause: Throwable) : WaitlistEntryUiState
}

sealed interface WaitlistEntryEvent {
    /** Lượt chờ vừa được đẩy lên thành đăng ký: mở vé, bỏ màn hình này khỏi back stack (AC 2.4). */
    data class Promoted(val sessionId: String) : WaitlistEntryEvent

    /** Bấm rời đúng lúc được đẩy lên: báo "Bạn đã được nhận vào buổi" rồi mở vé; đăng ký không bị hủy (FR-013). */
    data class AlreadyPromoted(val sessionId: String) : WaitlistEntryEvent
}

/**
 * Lượt chờ của mình ở một buổi (spec 081). Kết hợp buổi realtime (để tính vị trí) với đăng ký của mình. `requestId` của
 * thao tác rời sinh một lần, giữ trong [SavedStateHandle]: mất mạng hay bận rồi thử lại dùng cùng mã (FR-003, FR-016).
 */
@HiltViewModel
class WaitlistEntryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeSession: ObserveDropInSession,
    private val observeMine: ObserveMyDropInRegistration,
    private val leaveWaitlist: LeaveWaitlist,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID]) { "Thiếu sessionId" }

    val requestId: String = savedStateHandle.get<String>(KEY_REQUEST_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_REQUEST_ID] = it }

    private val _uiState = MutableStateFlow<WaitlistEntryUiState>(WaitlistEntryUiState.Loading)
    val uiState: StateFlow<WaitlistEntryUiState> = _uiState.asStateFlow()

    private val _events = Channel<WaitlistEntryEvent>(Channel.BUFFERED)
    val events: Flow<WaitlistEntryEvent> = _events.receiveAsFlow()

    private var observeJob: Job? = null
    private var promotedSent = false

    init {
        start()
    }

    fun retry() {
        if (_uiState.value !is WaitlistEntryUiState.Error) return
        _uiState.value = WaitlistEntryUiState.Loading
        start()
    }

    /** Xác nhận rời hàng chờ (sau hộp thoại "Không mất phí"). Chỉ chạy khi đang hiện nội dung. */
    fun confirmLeave() {
        val content = _uiState.value as? WaitlistEntryUiState.Content ?: return
        _uiState.value = WaitlistEntryUiState.Leaving(content)
        viewModelScope.launch {
            val result = try {
                leaveWaitlist(sessionId, requestId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            onLeaveResult(content, result)
        }
    }

    private suspend fun onLeaveResult(content: WaitlistEntryUiState.Content, result: Result<DropInRegistration>) {
        when (val error = result.exceptionOrNull()) {
            null -> _uiState.value = WaitlistEntryUiState.Left
            is DropInError.AlreadyPromoted -> {
                promotedSent = true
                // Trả màn hình về nội dung để không kẹt ở "Đang rời"; Fragment mở vé ngay sau thông báo.
                _uiState.value = content
                _events.send(WaitlistEntryEvent.AlreadyPromoted(sessionId))
            }
            // Mất mạng, bận, lỗi lạ: ở lại, bấm lại dùng cùng requestId. Không còn lượt thì dữ liệu realtime lo phần còn lại.
            else -> _uiState.value = content.copy(leaveError = error)
        }
    }

    private fun start() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(observeSession(sessionId), observeMine(sessionId)) { session, mine -> session to mine }
                .catch { error -> _uiState.value = WaitlistEntryUiState.Error(error) }
                .collect { (session, mine) -> onData(session, mine) }
        }
    }

    private suspend fun onData(session: DropInSession?, mine: DropInRegistration?) {
        // Đang gửi yêu cầu rời, hoặc đã rời: kết quả của yêu cầu quyết định, không để dữ liệu realtime ghi đè.
        val current = _uiState.value
        if (current is WaitlistEntryUiState.Leaving || current is WaitlistEntryUiState.Left) return
        when {
            mine == null -> _uiState.value = WaitlistEntryUiState.Empty
            mine.status == RegistrationStatus.REGISTERED || mine.status == RegistrationStatus.CHECKED_IN -> {
                if (!promotedSent) {
                    promotedSent = true
                    _events.send(WaitlistEntryEvent.Promoted(sessionId))
                }
            }
            mine.status == RegistrationStatus.CANCELLED ->
                _uiState.value = WaitlistEntryUiState.Ended(endReason(session, mine))
            else -> showWaiting(session, mine)
        }
    }

    private fun showWaiting(session: DropInSession?, mine: DropInRegistration) {
        // Buổi hoặc số vé chưa tới kịp (hai listener riêng): giữ trạng thái hiện tại, không đoán vị trí.
        val position = session?.let { WaitlistPosition.of(it, mine) } ?: return
        _uiState.value = WaitlistEntryUiState.Content(
            position = position.position,
            total = position.total,
            partySize = mine.partySize,
            amount = mine.amount,
            paymentMethod = mine.paymentMethod,
            session = session,
        )
    }

    private fun endReason(session: DropInSession?, mine: DropInRegistration): WaitlistEndReason? =
        mine.waitlistEndReason
            ?: WaitlistEndReason.SESSION_CANCELLED.takeIf { session?.status == SessionStatus.CANCELLED }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        internal const val KEY_REQUEST_ID = "dropin_waitlist_leave_request_id"
    }
}

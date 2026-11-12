package com.example.booking_app.feature.dropin.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject

/**
 * Nút chính ở chi tiết buổi (contracts/ui-screens.md mục 2). Thêm [OWN_SESSION] cho chủ cơ sở xem buổi của mình:
 * không có [REGISTER] (FR-011) nhưng cũng không phải "đã đủ" hay "đã đóng".
 */
enum class DetailAction { REGISTER, VIEW_TICKET, FULL, CLOSED, OWN_SESSION }

sealed interface DropInDetailUiState {
    data object Loading : DropInDetailUiState

    data class Content(
        val session: DropInSession,
        val myRegistration: DropInRegistration?,
        val action: DetailAction,
    ) : DropInDetailUiState

    /** Buổi không tồn tại (trạng thái rỗng của màn hình chi tiết). */
    data object NotFound : DropInDetailUiState

    data class Error(val cause: Throwable) : DropInDetailUiState
}

sealed interface DropInDetailEvent {
    /** Khách bấm "Đăng ký" (T041): mở đăng nhập kèm `returnTo`. */
    data class RequireLogin(val sessionId: String) : DropInDetailEvent

    data class OpenConfirm(val sessionId: String) : DropInDetailEvent

    data class OpenTicket(val sessionId: String) : DropInDetailEvent
}

/**
 * Chi tiết buổi (US1, T028): kết hợp buổi realtime với đăng ký của chính mình (chỉ khi đã đăng nhập).
 * Listener chỉ chạy khi có người thu thập [uiState] (`WhileSubscribed`), nên được gỡ khi Fragment dừng.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DropInDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeSession: ObserveDropInSession,
    private val observeMine: ObserveMyDropInRegistration,
    private val sessionManager: SessionManager,
    private val clock: AppClock,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID]) { "Thiếu sessionId" }

    private val reload = MutableStateFlow(0)

    private val _events = Channel<DropInDetailEvent>(Channel.BUFFERED)
    val events: Flow<DropInDetailEvent> = _events.receiveAsFlow()

    private val uidFlow: Flow<String?> = sessionManager.sessionState
        .map { (it as? SessionState.SignedIn)?.uid }
        .distinctUntilChanged()

    private val mineFlow: Flow<Pair<String?, DropInRegistration?>> = uidFlow.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(null to null)
        } else {
            observeMine(sessionId)
                .map<DropInRegistration?, Pair<String?, DropInRegistration?>> { uid to it }
                // Không đọc được đăng ký (vừa đăng xuất, mất quyền): vẫn hiện buổi.
                .catch { emit(uid to null) }
        }
    }

    val uiState: StateFlow<DropInDetailUiState> = reload
        .flatMapLatest {
            combine(observeSession(sessionId), mineFlow) { session, (uid, mine) -> build(session, uid, mine) }
                .onStart { emit(DropInDetailUiState.Loading) }
                .catch { emit(DropInDetailUiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DropInDetailUiState.Loading)

    fun retry() {
        reload.update { it + 1 }
    }

    fun onRegisterClicked() {
        val content = uiState.value as? DropInDetailUiState.Content ?: return
        if (content.action != DetailAction.REGISTER) return
        val event = if (sessionManager.currentUid() == null) {
            DropInDetailEvent.RequireLogin(sessionId)
        } else {
            DropInDetailEvent.OpenConfirm(sessionId)
        }
        _events.trySend(event)
    }

    fun onViewTicketClicked() {
        _events.trySend(DropInDetailEvent.OpenTicket(sessionId))
    }

    private fun build(session: DropInSession?, uid: String?, mine: DropInRegistration?): DropInDetailUiState {
        if (session == null) return DropInDetailUiState.NotFound
        return DropInDetailUiState.Content(session, mine, actionFor(session, uid, mine))
    }

    private fun actionFor(session: DropInSession, uid: String?, mine: DropInRegistration?): DetailAction {
        val started = !Instant.ofEpochMilli(clock.now()).isBefore(session.startAt)
        val closed = started ||
            session.status == SessionStatus.CANCELLED ||
            session.status == SessionStatus.COMPLETED ||
            !session.venueActive
        return when {
            mine?.isActive == true -> DetailAction.VIEW_TICKET
            closed -> DetailAction.CLOSED
            session.isOwnedBy(uid) -> DetailAction.OWN_SESSION
            session.status == SessionStatus.FULL || session.remaining <= 0 -> DetailAction.FULL
            else -> DetailAction.REGISTER
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

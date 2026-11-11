package com.example.booking_app.feature.dropin.ui.ticket

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject

/** Nhãn trạng thái trên vé (Chip Material 3, AC 3.5). */
enum class TicketLabel { SESSION_CHANGED, SESSION_CANCELLED, VENUE_INACTIVE }

/** Lý do vé không có mã QR. */
enum class NoQrReason { SESSION_CANCELLED, INACTIVE, PENDING }

sealed interface DropInTicketUiState {
    data object Loading : DropInTicketUiState

    /**
     * [qrToken] `null` khi không được hiện QR, lý do ở [noQrReason]. [session] có thể `null` khi đọc buổi lỗi: vé vẫn
     * hiện bằng thông tin chốt trong đăng ký. Bitmap QR do Fragment vẽ bằng [QrRenderer] để ViewModel test được trên JVM.
     */
    data class Content(
        val registration: DropInRegistration,
        val session: DropInSession?,
        val qrToken: String?,
        val noQrReason: NoQrReason?,
        val labels: List<TicketLabel>,
        val canCancel: Boolean,
    ) : DropInTicketUiState

    /** Chưa có đăng ký cho buổi này (trạng thái rỗng). */
    data object NotFound : DropInTicketUiState

    data class Error(val cause: Throwable) : DropInTicketUiState
}

/**
 * Vé vãng lai (US2, T040). Đọc realtime đăng ký của mình và buổi. Firestore giữ bản cache nên vé đã mở một lần vẫn
 * hiện được khi mất mạng (AC 3.3); trạng thái `Offline(cached)` riêng của contract làm ở US3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DropInTicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeMine: ObserveMyDropInRegistration,
    private val observeSession: ObserveDropInSession,
    private val clock: AppClock,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID]) { "Thiếu sessionId" }

    /** Vừa đăng ký xong (từ màn hình xác nhận): hiện dải "Đăng ký thành công". */
    val justRegistered: Boolean = savedStateHandle[ARG_JUST_REGISTERED] ?: false

    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<DropInTicketUiState> = reload
        .flatMapLatest {
            val sessionFlow = observeSession(sessionId)
                .map<DropInSession?, DropInSession?> { it }
                // Đọc buổi lỗi: vẫn hiện vé bằng thông tin chốt trong đăng ký.
                .catch { emit(null) }
            combine(observeMine(sessionId), sessionFlow, ::build)
                .onStart { emit(DropInTicketUiState.Loading) }
                .catch { emit(DropInTicketUiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DropInTicketUiState.Loading)

    fun retry() {
        reload.update { it + 1 }
    }

    private fun build(registration: DropInRegistration?, session: DropInSession?): DropInTicketUiState {
        if (registration == null) return DropInTicketUiState.NotFound
        val cancelled = session?.status == SessionStatus.CANCELLED
        val labels = buildList {
            if (cancelled) add(TicketLabel.SESSION_CANCELLED)
            else if (registration.sessionChanged) add(TicketLabel.SESSION_CHANGED)
            if (session != null && !session.venueActive) add(TicketLabel.VENUE_INACTIVE)
        }
        val noQrReason = when {
            cancelled -> NoQrReason.SESSION_CANCELLED
            !registration.isActive -> NoQrReason.INACTIVE
            registration.qrToken.isNullOrEmpty() -> NoQrReason.PENDING
            else -> null
        }
        val startAt = session?.startAt ?: registration.sessionStartAt
        val canCancel = registration.status == RegistrationStatus.REGISTERED &&
            !cancelled &&
            startAt != null &&
            Instant.ofEpochMilli(clock.now()).isBefore(startAt)
        return DropInTicketUiState.Content(
            registration = registration,
            session = session,
            qrToken = registration.qrToken.takeIf { noQrReason == null },
            noQrReason = noQrReason,
            labels = labels,
            canCancel = canCancel,
        )
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        const val ARG_JUST_REGISTERED = "justRegistered"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

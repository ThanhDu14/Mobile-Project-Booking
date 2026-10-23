package com.example.booking_app.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionAuthSource
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.VerificationEmailSender
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.core.util.countdownSeconds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyEmailBannerUiState(
    val visible: Boolean = false,
    val resendInSeconds: Int = 0,
    val isSending: Boolean = false,
)

sealed interface VerifyEmailBannerEvent {
    data object Sent : VerifyEmailBannerEvent

    data object SendFailed : VerifyEmailBannerEvent
}

/**
 * Banner "Hãy xác minh email" (FR-009, research R9). Chỉ hiện với tài khoản có mật khẩu chưa xác minh;
 * tài khoản Google hoặc chỉ có số điện thoại thì không hiện.
 */
@HiltViewModel
class VerifyEmailBannerViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authSource: SessionAuthSource,
    private val prefs: SessionPrefs,
    private val sender: VerificationEmailSender,
    private val clock: AppClock,
) : ViewModel() {

    /** Đã thấy xác minh sau `reload()`; ẩn ngay, không chờ token mới (mất mạng vẫn ẩn). */
    private val verifiedLocally = MutableStateFlow(false)
    private val resendInSeconds = MutableStateFlow(0)
    private val isSending = MutableStateFlow(false)
    private var countdownJob: Job? = null

    val uiState: StateFlow<VerifyEmailBannerUiState> = combine(
        sessionManager.sessionState,
        authSource.authState,
        verifiedLocally,
        resendInSeconds,
        isSending,
    ) { state, user, verified, seconds, sending ->
        val needsVerification = state is SessionState.SignedIn && !state.emailVerified && !verified &&
            user != null && PASSWORD_PROVIDER in user.providers
        VerifyEmailBannerUiState(visible = needsVerification, resendInSeconds = seconds, isSending = sending)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VerifyEmailBannerUiState())

    private val _events = Channel<VerifyEmailBannerEvent>(Channel.BUFFERED)
    val events: Flow<VerifyEmailBannerEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { prefs.lastVerificationEmailAt()?.let(::startCountdown) }
    }

    /** Gọi ở `onResume` của màn hình có banner: người dùng có thể vừa bấm link trong email. */
    fun onResume() {
        if (!uiState.value.visible) return
        viewModelScope.launch {
            val user = try {
                authSource.reload()
            } catch (e: AppError) {
                return@launch
            }
            if (user?.isEmailVerified == true) {
                verifiedLocally.value = true
                // Token mới có claim email_verified = true cho Rules và Edge Function.
                sessionManager.refresh(forceTokenRefresh = true)
            }
        }
    }

    fun onResend() {
        if (isSending.value || resendInSeconds.value > 0) return
        isSending.value = true
        viewModelScope.launch {
            val sent = sender.send()
            isSending.value = false
            if (sent) {
                prefs.lastVerificationEmailAt()?.let(::startCountdown)
                _events.send(VerifyEmailBannerEvent.Sent)
            } else {
                _events.send(VerifyEmailBannerEvent.SendFailed)
            }
        }
    }

    private fun startCountdown(sentAt: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            countdownSeconds(sentAt + RESEND_INTERVAL_MS, clock::now).collect { resendInSeconds.value = it }
        }
    }

    private companion object {
        const val PASSWORD_PROVIDER = "password"
        const val RESEND_INTERVAL_MS = 60_000L
    }
}

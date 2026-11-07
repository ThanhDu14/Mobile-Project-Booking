package com.example.booking_app.feature.auth.ui.forgot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.core.util.countdownSeconds
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.usecase.SendPasswordResetUseCase
import com.example.booking_app.feature.auth.domain.validation.EmailRule
import com.example.booking_app.feature.auth.domain.validation.Validated
import com.example.booking_app.feature.auth.domain.validation.ValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: ValidationError? = null,
    val isSubmitting: Boolean = false,
    /** Đã gửi: cùng một thông báo dù email có tài khoản hay không (FR-018). */
    val sent: Boolean = false,
    val resendInSeconds: Int = 0,
)

sealed interface ForgotPasswordEvent {
    data object ShowNetworkError : ForgotPasswordEvent

    data class ShowError(val error: AuthError) : ForgotPasswordEvent
}

/** Quên mật khẩu (US3, contracts/ui-screens.md §3). Arg `email` điền sẵn từ màn hình đăng nhập/đăng ký. */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val sendPasswordReset: SendPasswordResetUseCase,
    private val prefs: SessionPrefs,
    private val clock: AppClock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ForgotPasswordUiState(
            email = savedStateHandle[KEY_EMAIL] ?: "",
            sent = savedStateHandle[KEY_SENT] ?: false,
        ),
    )
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<ForgotPasswordEvent>(Channel.BUFFERED)
    val events: Flow<ForgotPasswordEvent> = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch { prefs.lastPasswordResetAt()?.let(::startCountdown) }
    }

    fun onEmailChanged(email: String) {
        savedStateHandle[KEY_EMAIL] = email
        _uiState.update { it.copy(email = email, emailError = null) }
    }

    /** "Gửi" lần đầu và "Gửi lại" sau 60 giây. */
    fun onSubmit() {
        val state = _uiState.value
        if (state.isSubmitting || state.resendInSeconds > 0) return
        val email = when (val v = EmailRule.validate(state.email)) {
            is Validated.Invalid -> {
                _uiState.update { it.copy(emailError = v.error) }
                return
            }
            is Validated.Valid -> v.value
        }
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = sendPasswordReset(email)
            _uiState.update { it.copy(isSubmitting = false) }
            when (result) {
                is AuthResult.Success -> {
                    savedStateHandle[KEY_SENT] = true
                    _uiState.update { it.copy(sent = true) }
                    prefs.lastPasswordResetAt()?.let(::startCountdown)
                }
                is AuthResult.Failure -> _events.send(
                    if (result.error == AuthError.Network) ForgotPasswordEvent.ShowNetworkError else ForgotPasswordEvent.ShowError(result.error),
                )
            }
        }
    }

    private fun startCountdown(sentAt: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            countdownSeconds(sentAt + RESEND_INTERVAL_MS, clock::now).collect { seconds ->
                _uiState.update { it.copy(resendInSeconds = seconds) }
                // data-model §3: mốc chỉ cần giữ trong 60 giây.
                if (seconds == 0) prefs.clearLastPasswordResetAt()
            }
        }
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_SENT = "sent"
        const val RESEND_INTERVAL_MS = 60_000L
    }
}

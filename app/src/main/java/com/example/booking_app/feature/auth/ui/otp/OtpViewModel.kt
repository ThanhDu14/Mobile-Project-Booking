package com.example.booking_app.feature.auth.ui.otp

import android.app.Activity
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.core.util.countdownSeconds
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSession
import com.example.booking_app.feature.auth.domain.model.PhoneNumber
import com.example.booking_app.feature.auth.domain.usecase.SendOtpResult
import com.example.booking_app.feature.auth.domain.usecase.SendOtpUseCase
import com.example.booking_app.feature.auth.domain.usecase.VerifyOtpResult
import com.example.booking_app.feature.auth.domain.usecase.VerifyOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OtpState { Editing, Verifying, WrongCode, Expired, Locked5 }

data class OtpUiState(
    val maskedPhone: String = "",
    val code: String = "",
    val remainingAttempts: Int = OtpSession.MAX_WRONG_ATTEMPTS,
    val resendInSeconds: Int = 0,
    val state: OtpState = OtpState.Editing,
    val isResending: Boolean = false,
) {
    val canSubmit: Boolean
        get() = code.length == CODE_LENGTH && (state == OtpState.Editing || state == OtpState.WrongCode)

    companion object {
        const val CODE_LENGTH = 6
    }
}

sealed interface OtpEvent {
    data object ShowNetworkError : OtpEvent

    data class ShowError(val error: AuthError) : OtpEvent
}

/**
 * Nhập mã OTP (US5, FR-016, research R8). Phiên OTP (không gồm mã đã nhập) giữ trong `SavedStateHandle` để xoay
 * màn hình hay hệ thống hủy process vẫn còn số lần sai và mốc 5 phút. Đúng mã thì `MainActivity` điều hướng.
 */
@HiltViewModel
class OtpViewModel @Inject constructor(
    private val verifyOtp: VerifyOtpUseCase,
    private val sendOtp: SendOtpUseCase,
    private val clock: AppClock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var session = OtpSession(
        phoneE164 = savedStateHandle[ARG_PHONE] ?: "",
        verificationId = savedStateHandle[ARG_VERIFICATION_ID] ?: "",
        resendToken = savedStateHandle.get<Parcelable>(ARG_RESEND_TOKEN),
        sentAtElapsed = savedStateHandle[ARG_SENT_AT] ?: 0L,
        wrongCount = savedStateHandle[KEY_WRONG_COUNT] ?: 0,
    )

    private val _uiState = MutableStateFlow(
        OtpUiState(
            maskedPhone = PhoneNumber(session.phoneE164).masked(),
            remainingAttempts = session.remainingAttempts,
            state = unusableState() ?: OtpState.Editing,
        ),
    )
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private val _events = Channel<OtpEvent>(Channel.BUFFERED)
    val events: Flow<OtpEvent> = _events.receiveAsFlow()

    private var timersJob: Job? = null

    init {
        startTimers()
    }

    /** Chỉ nhận chữ số, tối đa 6. Sửa mã sau khi sai thì về `Editing`. */
    fun onCodeChanged(code: String) {
        val digits = code.filter(Char::isDigit).take(OtpUiState.CODE_LENGTH)
        _uiState.update {
            it.copy(code = digits, state = if (it.state == OtpState.WrongCode && digits != it.code) OtpState.Editing else it.state)
        }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(state = OtpState.Verifying) }
        viewModelScope.launch {
            when (val result = verifyOtp(session, state.code)) {
                // SessionManager đổi trạng thái, MainActivity điều hướng; giữ Verifying để không bấm lại.
                VerifyOtpResult.Success -> Unit
                is VerifyOtpResult.WrongCode -> {
                    updateSession(result.session)
                    _uiState.update {
                        it.copy(
                            remainingAttempts = session.remainingAttempts,
                            state = if (session.remainingAttempts == 0) OtpState.Locked5 else OtpState.WrongCode,
                        )
                    }
                }
                is VerifyOtpResult.Failure -> when (result.error) {
                    AuthError.CodeExpired -> _uiState.update { it.copy(state = unusableState() ?: OtpState.Expired) }
                    AuthError.Network -> {
                        _uiState.update { it.copy(state = OtpState.Editing) }
                        _events.send(OtpEvent.ShowNetworkError)
                    }
                    else -> {
                        _uiState.update { it.copy(state = OtpState.Editing) }
                        if (result.error != AuthError.AccountLocked) _events.send(OtpEvent.ShowError(result.error))
                    }
                }
            }
        }
    }

    /** "Gửi lại mã": chỉ bật sau 60 giây. Phiên mới có `wrongCount = 0` và mốc 5 phút mới. */
    fun onResend(activity: Activity) {
        val state = _uiState.value
        if (state.resendInSeconds > 0 || state.isResending || state.state == OtpState.Verifying) return
        _uiState.update { it.copy(isResending = true) }
        viewModelScope.launch {
            val result = sendOtp(activity, session.phoneE164, session.resendToken)
            _uiState.update { it.copy(isResending = false) }
            when (result) {
                is AuthResult.Success -> when (val sent = result.value) {
                    is SendOtpResult.CodeSent -> {
                        updateSession(sent.session)
                        _uiState.update {
                            it.copy(code = "", remainingAttempts = session.remainingAttempts, state = OtpState.Editing)
                        }
                        startTimers()
                    }
                    SendOtpResult.SignedIn -> Unit
                }
                is AuthResult.Failure ->
                    _events.send(if (result.error == AuthError.Network) OtpEvent.ShowNetworkError else OtpEvent.ShowError(result.error))
            }
        }
    }

    /** Đếm 60 giây cho "Gửi lại mã" và hết hạn mã sau 5 phút, cùng tính từ lúc gửi. */
    private fun startTimers() {
        timersJob?.cancel()
        timersJob = viewModelScope.launch {
            launch {
                countdownSeconds(session.sentAtElapsed + RESEND_INTERVAL_MS, clock::elapsedRealtime)
                    .collect { seconds -> _uiState.update { it.copy(resendInSeconds = seconds) } }
            }
            launch {
                delay((session.sentAtElapsed + OtpSession.VALIDITY_MS - clock.elapsedRealtime()).coerceAtLeast(0))
                _uiState.update {
                    if (it.state == OtpState.Editing || it.state == OtpState.WrongCode) it.copy(state = OtpState.Expired) else it
                }
            }
        }
    }

    private fun unusableState(): OtpState? = when {
        session.remainingAttempts == 0 -> OtpState.Locked5
        !session.isUsable(clock.elapsedRealtime()) -> OtpState.Expired
        else -> null
    }

    private fun updateSession(newSession: OtpSession) {
        session = newSession
        savedStateHandle[ARG_VERIFICATION_ID] = newSession.verificationId
        savedStateHandle[ARG_RESEND_TOKEN] = newSession.resendToken
        savedStateHandle[ARG_SENT_AT] = newSession.sentAtElapsed
        savedStateHandle[KEY_WRONG_COUNT] = newSession.wrongCount
    }

    companion object {
        const val ARG_PHONE = "phoneE164"
        const val ARG_VERIFICATION_ID = "verificationId"
        const val ARG_SENT_AT = "sentAtElapsed"
        const val ARG_RESEND_TOKEN = "resendToken"
        private const val KEY_WRONG_COUNT = "wrongCount"
        private const val RESEND_INTERVAL_MS = 60_000L
    }
}

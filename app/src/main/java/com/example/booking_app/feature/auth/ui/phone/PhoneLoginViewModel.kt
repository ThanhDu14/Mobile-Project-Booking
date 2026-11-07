package com.example.booking_app.feature.auth.ui.phone

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSession
import com.example.booking_app.feature.auth.domain.usecase.SendOtpResult
import com.example.booking_app.feature.auth.domain.usecase.SendOtpUseCase
import com.example.booking_app.feature.auth.domain.validation.PhoneRule
import com.example.booking_app.feature.auth.domain.validation.Validated
import com.example.booking_app.feature.auth.domain.validation.ValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneLoginUiState(
    val phone: String = "",
    val phoneError: ValidationError? = null,
    /** "Không gửi được mã tới số này…" hiện dưới ô số (số không phải số thử nghiệm, R8). */
    val sendError: AuthError? = null,
    val isSending: Boolean = false,
)

sealed interface PhoneLoginEvent {
    data class OpenOtp(val session: OtpSession) : PhoneLoginEvent

    data object ShowNetworkError : PhoneLoginEvent

    data class ShowError(val error: AuthError) : PhoneLoginEvent
}

/** Nhập số điện thoại (US5, FR-017). Gửi được mã thì sang màn hình OTP kèm phiên OTP. */
@HiltViewModel
class PhoneLoginViewModel @Inject constructor(
    private val sendOtp: SendOtpUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneLoginUiState(phone = savedStateHandle[KEY_PHONE] ?: ""))
    val uiState: StateFlow<PhoneLoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<PhoneLoginEvent>(Channel.BUFFERED)
    val events: Flow<PhoneLoginEvent> = _events.receiveAsFlow()

    fun onPhoneChanged(phone: String) {
        savedStateHandle[KEY_PHONE] = phone
        _uiState.update { it.copy(phone = phone, phoneError = null, sendError = null) }
    }

    fun onSubmit(activity: Activity) {
        val state = _uiState.value
        if (state.isSending) return
        val phone = withLeadingZero(state.phone)
        if (PhoneRule.validate(phone) is Validated.Invalid) {
            _uiState.update { it.copy(phoneError = ValidationError.PHONE_FORMAT) }
            return
        }
        _uiState.update { it.copy(isSending = true, sendError = null) }
        viewModelScope.launch {
            val result = sendOtp(activity, phone)
            _uiState.update { it.copy(isSending = false) }
            when (result) {
                is AuthResult.Success -> when (val sent = result.value) {
                    is SendOtpResult.CodeSent -> _events.send(PhoneLoginEvent.OpenOtp(sent.session))
                    SendOtpResult.SignedIn -> Unit
                }
                is AuthResult.Failure -> when (result.error) {
                    AuthError.PhoneNotSupported -> _uiState.update { it.copy(sendError = result.error) }
                    AuthError.Network -> _events.send(PhoneLoginEvent.ShowNetworkError)
                    // SessionManager tự đăng xuất và mở thông báo khóa.
                    AuthError.AccountLocked -> Unit
                    else -> _events.send(PhoneLoginEvent.ShowError(result.error))
                }
            }
        }
    }

    /** Ô "+84" hiện sẵn bên cạnh nên người dùng hay gõ `901 234 567`; thêm số 0 đầu cho `PhoneRule`. */
    private fun withLeadingZero(phone: String): String {
        val trimmed = phone.trim()
        return if (trimmed.firstOrNull() in MOBILE_PREFIXES) "0$trimmed" else trimmed
    }

    private companion object {
        const val KEY_PHONE = "phone"
        val MOBILE_PREFIXES = setOf('3', '5', '7', '8', '9')
    }
}

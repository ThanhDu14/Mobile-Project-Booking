package com.example.booking_app.feature.account.ui.reauth

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.account.data.AccountErrorMapper
import com.example.booking_app.feature.account.data.GoogleReauthTokenSource
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.ReauthMethod
import com.example.booking_app.feature.account.domain.usecase.ReauthenticateUseCase
import com.example.booking_app.feature.account.ui.accountErrorMessage
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSession
import com.example.booking_app.feature.auth.domain.usecase.SendOtpResult
import com.example.booking_app.feature.auth.domain.usecase.SendOtpUseCase
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

data class ReauthUiState(
    /** `null`: không xác định được cách xác nhận (đã đăng xuất ở nơi khác). */
    val method: ReauthMethod? = null,
    /** Đã gửi OTP, đang chờ nhập mã. Domain của 010 thực thi 5 phút và 5 lần sai. */
    val otpSession: OtpSession? = null,
    @StringRes val error: Int? = null,
    val isSubmitting: Boolean = false,
)

sealed interface ReauthEvent {
    data object Done : ReauthEvent
}

/**
 * Xác nhận lại danh tính (research R8, FR-017). `Activity` chỉ được truyền vào từng lời gọi cần nó (Credential
 * Manager, `verifyPhoneNumber`), không giữ lại, giống `PhoneLoginViewModel` của 010.
 */
@HiltViewModel
class ReauthViewModel @Inject constructor(
    private val reauthenticate: ReauthenticateUseCase,
    private val googleTokens: GoogleReauthTokenSource,
    private val sendOtp: SendOtpUseCase,
    private val clock: AppClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReauthUiState(method = reauthenticate.method()))
    val uiState: StateFlow<ReauthUiState> = _uiState.asStateFlow()

    private val _events = Channel<ReauthEvent>(Channel.BUFFERED)
    val events: Flow<ReauthEvent> = _events.receiveAsFlow()

    fun onSubmitPassword(password: String) {
        if (password.isEmpty()) return
        submit { reauthenticate.withPassword(password) }
    }

    fun onGoogle(activity: Activity) = submit {
        val idToken = try {
            googleTokens.requestIdToken(activity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AccountErrorMapper.map(e)
        }
        reauthenticate.withGoogle(idToken)
    }

    /** Gửi hoặc gửi lại OTP tới số hiện tại. Máy tự xác minh được thì xong luôn. */
    fun onSendOtp(activity: Activity) {
        val phone = (_uiState.value.method as? ReauthMethod.Phone)?.phoneE164 ?: return
        val resendToken = _uiState.value.otpSession?.resendToken
        submitOrWait {
            when (val result = sendOtp(activity, phone, resendToken)) {
                is AuthResult.Failure -> throw AccountErrorMapper.map(result.error)
                is AuthResult.Success -> when (val sent = result.value) {
                    is SendOtpResult.CodeSent -> {
                        _uiState.update { it.copy(otpSession = sent.session) }
                        false
                    }
                    SendOtpResult.SignedIn -> {
                        reauthenticate.afterPhoneAutoVerified()
                        true
                    }
                }
            }
        }
    }

    fun onSubmitCode(code: String) {
        val session = _uiState.value.otpSession ?: return
        if (code.length != CODE_LENGTH) return
        if (!session.isUsable(clock.elapsedRealtime())) {
            _uiState.update { it.copy(error = accountErrorMessage(AccountError.OtpExpired)) }
            return
        }
        submit {
            try {
                reauthenticate.withPhoneCode(session.verificationId, code)
            } catch (e: AccountError.WrongOtpCode) {
                _uiState.update { it.copy(otpSession = session.copy(wrongCount = session.wrongCount + 1)) }
                throw e
            }
        }
    }

    private fun submit(block: suspend () -> Unit) = submitOrWait { block(); true }

    /** [block] trả `true` khi đã xác nhận xong, `false` khi còn chờ bước sau (nhập OTP). */
    private fun submitOrWait(block: suspend () -> Boolean) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                val done = block()
                _uiState.update { it.copy(isSubmitting = false) }
                if (done) _events.send(ReauthEvent.Done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Người dùng tự hủy hộp thoại Google: không báo lỗi.
                val message = if (e == AccountError.ReauthCancelled) null else accountErrorMessage(e)
                _uiState.update { it.copy(isSubmitting = false, error = message) }
            }
        }
    }

    private companion object {
        const val CODE_LENGTH = 6
    }
}

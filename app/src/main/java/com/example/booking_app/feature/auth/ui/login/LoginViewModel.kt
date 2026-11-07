package com.example.booking_app.feature.auth.ui.login

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.usecase.LinkGoogleToPasswordUseCase
import com.example.booking_app.feature.auth.domain.usecase.SignInWithEmailUseCase
import com.example.booking_app.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.example.booking_app.feature.auth.domain.validation.EmailRule
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

data class LoginUiState(
    val email: String = "",
    val emailError: ValidationError? = null,
    val passwordError: ValidationError? = null,
    val isSubmitting: Boolean = false,
    /** Lỗi chung của form (sai thông tin), không gắn với ô nào (FR-011). */
    val formError: AuthError? = null,
    val isGoogleSubmitting: Boolean = false,
    val isLinking: Boolean = false,
    /** Lỗi trong hộp thoại liên kết tài khoản (sai mật khẩu). */
    val linkError: AuthError? = null,
)

sealed interface LoginEvent {
    data object ShowLocked : LoginEvent

    data object ShowNetworkError : LoginEvent

    data object ClearPassword : LoginEvent

    /** Lỗi hiện bằng snackbar (quá nhiều lần thử, lỗi không rõ). */
    data class ShowError(val error: AuthError) : LoginEvent

    /** Email đã có tài khoản mật khẩu: hỏi mật khẩu để liên kết Google (FR-012). */
    data class OpenLinkDialog(val email: String) : LoginEvent

    data object LinkDone : LoginEvent
}

/**
 * Màn hình đăng nhập (contracts/ui-screens.md §3). Điều hướng sau khi đăng nhập do `MainActivity` làm theo
 * `SessionState`, kể cả `returnTo` (đọc từ argument của màn hình này trên back stack).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val linkGoogleToPassword: LinkGoogleToPasswordUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(email = savedStateHandle[KEY_EMAIL] ?: ""))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    // Không lưu vào SavedStateHandle (research R13).
    private var password = ""

    // Google credential đang chờ liên kết. Mất khi process bị hủy: người dùng bấm Google lại.
    private var pendingLink: AuthError.NeedsLinking? = null

    init {
        val lockedNotice = savedStateHandle.get<Boolean>(ARG_LOCKED_NOTICE) == true
        if (lockedNotice && savedStateHandle.get<Boolean>(KEY_LOCKED_SHOWN) != true) {
            savedStateHandle[KEY_LOCKED_SHOWN] = true
            _events.trySend(LoginEvent.ShowLocked)
        }
    }

    fun onEmailChanged(email: String) {
        savedStateHandle[KEY_EMAIL] = email
        _uiState.update { it.copy(email = email, emailError = null, formError = null) }
    }

    fun onPasswordChanged(password: String) {
        this.password = password
        _uiState.update { it.copy(passwordError = null, formError = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isSubmitting || state.isGoogleSubmitting) return
        val email = EmailRule.validate(state.email)
        val passwordError = if (password.isEmpty()) ValidationError.PASSWORD_REQUIRED else null
        if (email is Validated.Invalid || passwordError != null) {
            _uiState.update {
                it.copy(emailError = (email as? Validated.Invalid)?.error, passwordError = passwordError, formError = null)
            }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            val result = signInWithEmail((email as Validated.Valid).value, password)
            _uiState.update { it.copy(isSubmitting = false) }
            if (result is AuthResult.Failure) onFailure(result.error)
        }
    }

    /** "Tiếp tục với Google". Hủy bottom sheet thì ở lại, không báo lỗi (US4-4). */
    fun onGoogle(activity: Activity) {
        val state = _uiState.value
        if (state.isSubmitting || state.isGoogleSubmitting) return
        _uiState.update { it.copy(isGoogleSubmitting = true, formError = null) }
        viewModelScope.launch {
            val result = signInWithGoogle(activity)
            _uiState.update { it.copy(isGoogleSubmitting = false) }
            if (result is AuthResult.Failure) onFailure(result.error)
        }
    }

    fun onLinkSubmit(password: String) {
        val pending = pendingLink ?: return
        val credential = pending.pendingCredential ?: return
        if (_uiState.value.isLinking) return
        _uiState.update { it.copy(isLinking = true, linkError = null) }
        viewModelScope.launch {
            val result = linkGoogleToPassword(pending.email.orEmpty(), password, credential)
            _uiState.update { it.copy(isLinking = false) }
            when {
                result is AuthResult.Success -> {
                    pendingLink = null
                    _events.send(LoginEvent.LinkDone)
                }
                (result as AuthResult.Failure).error == AuthError.InvalidCredentials ->
                    _uiState.update { it.copy(linkError = result.error) }
                else -> onFailure(result.error)
            }
        }
    }

    fun onLinkCancelled() {
        pendingLink = null
        _uiState.update { it.copy(linkError = null) }
    }

    private suspend fun onFailure(error: AuthError) {
        when (error) {
            AuthError.GoogleCancelled -> Unit
            is AuthError.NeedsLinking -> {
                pendingLink = error
                _events.send(LoginEvent.OpenLinkDialog(error.email.orEmpty()))
            }
            AuthError.InvalidCredentials -> _uiState.update { it.copy(formError = error) }
            AuthError.AccountLocked -> _events.send(LoginEvent.ShowLocked)
            AuthError.Network -> {
                password = ""
                _events.send(LoginEvent.ClearPassword)
                _events.send(LoginEvent.ShowNetworkError)
            }
            else -> _events.send(LoginEvent.ShowError(error))
        }
    }

    companion object {
        const val ARG_LOCKED_NOTICE = "lockedNotice"
        private const val KEY_EMAIL = "email"
        private const val KEY_LOCKED_SHOWN = "lockedShown"
    }
}

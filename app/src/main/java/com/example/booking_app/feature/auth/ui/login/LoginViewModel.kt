package com.example.booking_app.feature.auth.ui.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.usecase.SignInWithEmailUseCase
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
)

sealed interface LoginEvent {
    data object ShowLocked : LoginEvent

    data object ShowNetworkError : LoginEvent

    data object ClearPassword : LoginEvent

    /** Lỗi hiện bằng snackbar (quá nhiều lần thử, lỗi không rõ). */
    data class ShowError(val error: AuthError) : LoginEvent
}

/**
 * Màn hình đăng nhập (contracts/ui-screens.md §3). Điều hướng sau khi đăng nhập do `MainActivity` làm theo
 * `SessionState`, kể cả `returnTo` (đọc từ argument của màn hình này trên back stack).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInWithEmail: SignInWithEmailUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(email = savedStateHandle[KEY_EMAIL] ?: ""))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    // Không lưu vào SavedStateHandle (research R13).
    private var password = ""

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
        if (state.isSubmitting) return
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

    private suspend fun onFailure(error: AuthError) {
        when (error) {
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

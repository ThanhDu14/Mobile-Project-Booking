package com.example.booking_app.feature.auth.ui.register

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.usecase.CompleteSignupResult
import com.example.booking_app.feature.auth.domain.usecase.RegisterWithEmailUseCase
import com.example.booking_app.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.example.booking_app.feature.auth.domain.validation.ConfirmPasswordRule
import com.example.booking_app.feature.auth.domain.validation.EmailRule
import com.example.booking_app.feature.auth.domain.validation.NameRule
import com.example.booking_app.feature.auth.domain.validation.PasswordChecks
import com.example.booking_app.feature.auth.domain.validation.PasswordRule
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

enum class RegisterField { NAME, EMAIL, PASSWORD, CONFIRM_PASSWORD }

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val nameError: ValidationError? = null,
    val emailError: ValidationError? = null,
    val passwordError: ValidationError? = null,
    val confirmError: ValidationError? = null,
    /** "Email đã được sử dụng" kèm link Đăng nhập và quên mật khẩu (US1-3). */
    val emailInUse: Boolean = false,
    val passwordChecks: PasswordChecks = PasswordChecks(len8 = false, letterDigit = false),
    val termsChecked: Boolean = false,
    val isSubmitting: Boolean = false,
    val isGoogleSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = termsChecked && !isSubmitting && !isGoogleSubmitting
}

sealed interface RegisterEvent {
    data object ShowNetworkError : RegisterEvent

    data class ShowError(val error: AuthError) : RegisterEvent

    /** Google trùng email tài khoản mật khẩu: đăng nhập ở màn hình đăng nhập để liên kết (FR-012). */
    data object OpenLogin : RegisterEvent
}

/**
 * Đăng ký email (US1, contracts/ui-screens.md §3). Lỗi từng ô hiện khi rời ô hoặc bấm "Đăng ký" (FR-020).
 * Đăng ký xong `SessionManager` chuyển `SignedIn` và `MainActivity` điều hướng; ở đây chỉ xử lý lỗi.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerWithEmail: RegisterWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RegisterUiState(
            name = savedStateHandle[KEY_NAME] ?: "",
            email = savedStateHandle[KEY_EMAIL] ?: "",
            termsChecked = savedStateHandle[KEY_TERMS] ?: false,
        ),
    )
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
    val events: Flow<RegisterEvent> = _events.receiveAsFlow()

    // Không lưu vào SavedStateHandle (research R13). Giữ trong bộ nhớ để "Thử lại" không phải nhập lại.
    private var password = ""
    private var confirmPassword = ""

    fun onNameChanged(name: String) {
        savedStateHandle[KEY_NAME] = name
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onEmailChanged(email: String) {
        savedStateHandle[KEY_EMAIL] = email
        _uiState.update { it.copy(email = email, emailError = null, emailInUse = false) }
    }

    fun onPasswordChanged(password: String) {
        this.password = password
        _uiState.update { it.copy(passwordChecks = PasswordRule.check(password), passwordError = null, confirmError = null) }
    }

    fun onConfirmPasswordChanged(confirm: String) {
        confirmPassword = confirm
        _uiState.update { it.copy(confirmError = null) }
    }

    fun onTermsChecked(checked: Boolean) {
        savedStateHandle[KEY_TERMS] = checked
        _uiState.update { it.copy(termsChecked = checked) }
    }

    /** Rời một ô đã nhập thì kiểm tra ô đó; ô còn trống thì chờ tới lúc bấm "Đăng ký". */
    fun onFieldFocusLost(field: RegisterField) {
        val state = _uiState.value
        _uiState.update {
            when (field) {
                RegisterField.NAME -> if (state.name.isEmpty()) it else it.copy(nameError = NameRule.validate(state.name).error())
                RegisterField.EMAIL -> if (state.email.isEmpty()) it else it.copy(emailError = EmailRule.validate(state.email).error())
                RegisterField.PASSWORD -> if (password.isEmpty()) it else it.copy(passwordError = PasswordRule.validate(password).error())
                RegisterField.CONFIRM_PASSWORD ->
                    if (confirmPassword.isEmpty()) it else it.copy(confirmError = ConfirmPasswordRule.validate(password, confirmPassword).error())
            }
        }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val name = NameRule.validate(state.name)
        val email = EmailRule.validate(state.email)
        val passwordError = PasswordRule.validate(password).error()
        val confirmError = ConfirmPasswordRule.validate(password, confirmPassword).error()
        if (name is Validated.Invalid || email is Validated.Invalid || passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    nameError = name.error(),
                    emailError = email.error(),
                    passwordError = passwordError,
                    confirmError = confirmError,
                )
            }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, emailInUse = false) }
        viewModelScope.launch {
            val result = registerWithEmail(
                name = (name as Validated.Valid).value,
                email = (email as Validated.Valid).value,
                password = password,
            )
            _uiState.update { it.copy(isSubmitting = false) }
            when (result) {
                is CompleteSignupResult.Success -> Unit
                CompleteSignupResult.InvalidDisplayName -> _uiState.update { it.copy(nameError = ValidationError.NAME_LENGTH) }
                // Không xảy ra vì luôn gửi termsAccepted = true.
                CompleteSignupResult.TermsRequired -> _uiState.update { it.copy(termsChecked = false) }
                is CompleteSignupResult.Failure -> onFailure(result.error)
            }
        }
    }

    /** Nút "Google": cùng luồng với màn hình đăng nhập (US4). Hủy bottom sheet thì ở lại, không báo lỗi. */
    fun onGoogle(activity: Activity) {
        val state = _uiState.value
        if (state.isSubmitting || state.isGoogleSubmitting) return
        _uiState.update { it.copy(isGoogleSubmitting = true) }
        viewModelScope.launch {
            val result = signInWithGoogle(activity)
            _uiState.update { it.copy(isGoogleSubmitting = false) }
            if (result is AuthResult.Failure) onFailure(result.error)
        }
    }

    private suspend fun onFailure(error: AuthError) {
        when (error) {
            AuthError.EmailInUse -> _uiState.update { it.copy(emailInUse = true) }
            AuthError.Network -> _events.send(RegisterEvent.ShowNetworkError)
            AuthError.GoogleCancelled -> Unit
            is AuthError.NeedsLinking -> _events.send(RegisterEvent.OpenLogin)
            // SessionManager tự đăng xuất và mở thông báo khóa.
            AuthError.AccountLocked -> Unit
            else -> _events.send(RegisterEvent.ShowError(error))
        }
    }

    private fun Validated<*>.error(): ValidationError? = (this as? Validated.Invalid)?.error

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_TERMS = "termsChecked"
    }
}

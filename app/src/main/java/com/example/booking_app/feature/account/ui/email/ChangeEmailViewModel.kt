package com.example.booking_app.feature.account.ui.email

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.R
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.usecase.ChangeEmailUseCase
import com.example.booking_app.feature.account.ui.accountErrorMessage
import com.example.booking_app.feature.account.ui.isRetryable
import com.example.booking_app.feature.auth.domain.validation.EmailRule
import com.example.booking_app.feature.auth.domain.validation.Validated
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

data class ChangeEmailUiState(
    val email: String = "",
    val password: String = "",
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val isSubmitting: Boolean = false,
    /** Đã gửi link: hiện thông báo chung của R10, không cho biết email đã có chủ hay chưa (US7-2). */
    val sent: Boolean = false,
) {
    val canSubmit: Boolean get() = !isSubmitting
}

data class ChangeEmailError(@StringRes val message: Int, val retryable: Boolean)

/** Đổi email đăng nhập (US7, FR-016, research R10). Mật khẩu không lưu vào `SavedStateHandle`. */
@HiltViewModel
class ChangeEmailViewModel @Inject constructor(
    private val changeEmail: ChangeEmailUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeEmailUiState())
    val uiState: StateFlow<ChangeEmailUiState> = _uiState.asStateFlow()

    private val _errors = Channel<ChangeEmailError>(Channel.BUFFERED)
    val errors: Flow<ChangeEmailError> = _errors.receiveAsFlow()

    val currentEmail: String? get() = changeEmail.currentEmail()

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, emailError = null) }

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, passwordError = null) }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val email = (EmailRule.validate(state.email) as? Validated.Valid)?.value
        val emailError = when {
            email == null -> R.string.auth_error_email_format
            email == changeEmail.currentEmail() -> R.string.account_error_same_email
            else -> null
        }
        val passwordError = if (state.password.isEmpty()) R.string.account_error_current_password_required else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, sent = false) }
        viewModelScope.launch {
            try {
                changeEmail(email!!, state.password)
                _uiState.update { it.copy(isSubmitting = false, sent = true, password = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    private suspend fun onFailure(error: Exception) {
        when (error) {
            AccountError.WrongCurrentPassword ->
                _uiState.update { it.copy(isSubmitting = false, passwordError = R.string.account_error_wrong_password) }
            AccountError.SameEmail ->
                _uiState.update { it.copy(isSubmitting = false, emailError = R.string.account_error_same_email) }
            else -> {
                // FR-023: giữ email đã nhập, xóa mật khẩu.
                _uiState.update { it.copy(isSubmitting = false, password = "") }
                _errors.send(ChangeEmailError(accountErrorMessage(error), isRetryable(error)))
            }
        }
    }
}

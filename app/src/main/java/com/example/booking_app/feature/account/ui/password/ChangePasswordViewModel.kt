package com.example.booking_app.feature.account.ui.password

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.R
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.usecase.ChangePasswordUseCase
import com.example.booking_app.feature.account.domain.validation.DifferentPasswordRule
import com.example.booking_app.feature.account.ui.accountErrorMessage
import com.example.booking_app.feature.account.ui.isRetryable
import com.example.booking_app.feature.auth.domain.validation.ConfirmPasswordRule
import com.example.booking_app.feature.auth.domain.validation.PasswordChecks
import com.example.booking_app.feature.auth.domain.validation.PasswordRule
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

data class ChangePasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    @StringRes val currentError: Int? = null,
    @StringRes val newError: Int? = null,
    @StringRes val confirmError: Int? = null,
    val checks: PasswordChecks = PasswordChecks(len8 = false, letterDigit = false),
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = !isSubmitting
}

sealed interface ChangePasswordEvent {
    /** Đã đổi: quay lại trang Tài khoản và hiện "Đã đổi mật khẩu". */
    data object Changed : ChangePasswordEvent

    data class ShowError(@StringRes val message: Int, val retryable: Boolean) : ChangePasswordEvent
}

/**
 * Đổi mật khẩu (US5, FR-014, research R9). Kiểm tra ở app bằng `PasswordRule`, `ConfirmPasswordRule` của 010 và
 * [DifferentPasswordRule]; có lỗi thì không gọi Firebase. Mật khẩu không lưu vào `SavedStateHandle`.
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val changePassword: ChangePasswordUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events: Flow<ChangePasswordEvent> = _events.receiveAsFlow()

    fun onCurrentChanged(value: String) = _uiState.update { it.copy(current = value, currentError = null) }

    fun onNewChanged(value: String) =
        _uiState.update { it.copy(new = value, newError = null, checks = PasswordRule.check(value)) }

    fun onConfirmChanged(value: String) = _uiState.update { it.copy(confirm = value, confirmError = null) }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val currentError = if (state.current.isEmpty()) R.string.account_error_current_password_required else null
        val newError = when {
            PasswordRule.validate(state.new) is Validated.Invalid -> R.string.account_error_weak_password
            else -> DifferentPasswordRule.validate(state.current, state.new)?.let(::accountErrorMessage)
        }
        val confirmError = (ConfirmPasswordRule.validate(state.new, state.confirm) as? Validated.Invalid)
            ?.let { R.string.account_error_password_mismatch }
        if (currentError != null || newError != null || confirmError != null) {
            _uiState.update { it.copy(currentError = currentError, newError = newError, confirmError = confirmError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                changePassword(state.current, state.new)
                _uiState.update { it.copy(isSubmitting = false) }
                _events.send(ChangePasswordEvent.Changed)
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
                _uiState.update { it.copy(isSubmitting = false, currentError = R.string.account_error_wrong_password) }
            AccountError.WeakPassword, AccountError.SamePassword ->
                _uiState.update { it.copy(isSubmitting = false, newError = accountErrorMessage(error)) }
            else -> {
                // FR-023: giữ dữ liệu đã nhập trừ mật khẩu; màn hình này chỉ có ô mật khẩu.
                _uiState.update { ChangePasswordUiState() }
                _events.send(ChangePasswordEvent.ShowError(accountErrorMessage(error), isRetryable(error)))
            }
        }
    }
}

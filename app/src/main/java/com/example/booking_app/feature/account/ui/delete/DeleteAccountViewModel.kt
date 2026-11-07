package com.example.booking_app.feature.account.ui.delete

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.R
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.DeletionImpact
import com.example.booking_app.feature.account.domain.model.ReauthMethod
import com.example.booking_app.feature.account.domain.usecase.DeleteAccountUseCase
import com.example.booking_app.feature.account.domain.usecase.LoadDeletionImpactUseCase
import com.example.booking_app.feature.account.domain.usecase.ReauthenticateUseCase
import com.example.booking_app.feature.account.domain.validation.DeleteConfirmRule
import com.example.booking_app.feature.account.ui.accountErrorMessage
import com.example.booking_app.feature.account.ui.isRetryable
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

sealed interface ImpactState {
    data object Loading : ImpactState

    data class Error(@StringRes val message: Int, val retryable: Boolean) : ImpactState

    data class Content(val impact: DeletionImpact) : ImpactState
}

sealed interface ReauthState {
    /** [method] `Password`: ô mật khẩu ngay trên màn hình; `Google`, `Phone`: mở `ReauthDialog`. */
    data class Required(val method: ReauthMethod?) : ReauthState

    data object Done : ReauthState
}

data class DeleteAccountUiState(
    val impact: ImpactState = ImpactState.Loading,
    val reauth: ReauthState = ReauthState.Required(null),
    val password: String = "",
    val confirmText: String = "",
    val acknowledged: Boolean = false,
    val isDeleting: Boolean = false,
    @StringRes val passwordError: Int? = null,
    @StringRes val confirmError: Int? = null,
) {
    private val identityReady: Boolean
        get() = when (reauth) {
            ReauthState.Done -> true
            is ReauthState.Required -> reauth.method == ReauthMethod.Password && password.isNotEmpty()
        }

    /** Nút "Xóa vĩnh viễn tài khoản": đã xác nhận danh tính, gõ đúng "XÓA" và đánh dấu (US6-2). */
    val canDelete: Boolean
        get() = impact is ImpactState.Content && !isDeleting && identityReady && acknowledged &&
            DeleteConfirmRule.validate(confirmText) == null
}

sealed interface DeleteAccountEvent {
    /** Mở `ReauthDialog` (Google, số điện thoại) hoặc focus ô mật khẩu. */
    data object OpenReauth : DeleteAccountEvent

    data class ShowError(@StringRes val message: Int, val retryable: Boolean = false) : DeleteAccountEvent
}

/**
 * Xóa tài khoản (US6, FR-018–FR-022, research R11). Thành công thì `DeleteAccountUseCase` đăng xuất với lý do
 * `ACCOUNT_DELETED`, `MainActivity` tự về trang chủ khách và hiện "Tài khoản đã được xóa".
 */
@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val loadImpact: LoadDeletionImpactUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val reauthenticate: ReauthenticateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState(reauth = ReauthState.Required(reauthenticate.method())))
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeleteAccountEvent>(Channel.BUFFERED)
    val events: Flow<DeleteAccountEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun onRetryLoad() = load()

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, passwordError = null) }

    fun onConfirmTextChanged(value: String) = _uiState.update { it.copy(confirmText = value, confirmError = null) }

    fun onAcknowledgedChanged(value: Boolean) = _uiState.update { it.copy(acknowledged = value) }

    /** `ReauthDialog` báo đã xác nhận xong (Fragment Result `AccountResults.REAUTH`). */
    fun onReauthDone() = _uiState.update { it.copy(reauth = ReauthState.Done) }

    fun onDelete() {
        val state = _uiState.value
        if (!state.canDelete) return
        val impact = (state.impact as ImpactState.Content).impact
        val password = state.password.takeIf { state.reauth is ReauthState.Required }
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                deleteAccount(impact.impactVersion, state.confirmText, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(impact = ImpactState.Loading) }
        viewModelScope.launch {
            val next = try {
                ImpactState.Content(loadImpact())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImpactState.Error(accountErrorMessage(e), isRetryable(e) || e is AccountError.Unknown)
            }
            _uiState.update { it.copy(impact = next) }
        }
    }

    private suspend fun onFailure(error: Exception) {
        _uiState.update { it.copy(isDeleting = false) }
        when (error) {
            is AccountError.ImpactChanged ->
                // Có đơn hoặc lượt mới: người dùng phải đọc lại và đánh dấu lại (US6, contracts §1.4).
                _uiState.update { it.copy(impact = ImpactState.Content(error.newImpact), acknowledged = false) }
            AccountError.ReauthRequired, AccountError.ReauthCancelled, AccountError.ReauthWrongAccount -> {
                _uiState.update {
                    it.copy(reauth = ReauthState.Required(reauthenticate.method()), password = "")
                }
                if (error != AccountError.ReauthRequired) {
                    _events.send(DeleteAccountEvent.ShowError(accountErrorMessage(error)))
                }
                _events.send(DeleteAccountEvent.OpenReauth)
            }
            AccountError.WrongCurrentPassword ->
                _uiState.update { it.copy(passwordError = R.string.account_error_wrong_password) }
            AccountError.DeleteConfirmMismatch ->
                _uiState.update { it.copy(confirmError = R.string.account_error_delete_confirm) }
            AccountError.AdminSelfDelete, AccountError.DeleteTooLarge, AccountError.DeleteResultUnknown ->
                _events.send(DeleteAccountEvent.ShowError(accountErrorMessage(error)))
            AccountError.Network ->
                // Lỗi trước khi gọi confirm (khi xác nhận mật khẩu): chưa có gì đổi.
                _events.send(DeleteAccountEvent.ShowError(R.string.account_error_network, retryable = true))
            else -> _events.send(DeleteAccountEvent.ShowError(R.string.account_error_delete_failed))
        }
    }
}

package com.example.booking_app.feature.auth.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.usecase.CompleteSignupResult
import com.example.booking_app.feature.auth.domain.usecase.CompleteSignupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AccountSetupUiState {
    data object Loading : AccountSetupUiState

    data class Failed(val error: AppError) : AccountSetupUiState
}

/**
 * Màn hình chờ khởi tạo (Edge Case "khởi tạo lỗi", FR-006). Mở khi app thấy `Initializing` (đóng app sau
 * khi bấm "Đăng ký" mà `on-signup` chưa xong) hoặc `InitFailed`. Xong thì `MainActivity` điều hướng.
 */
@HiltViewModel
class AccountSetupViewModel @Inject constructor(
    private val prefs: SessionPrefs,
    private val completeSignup: CompleteSignupUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountSetupUiState>(AccountSetupUiState.Loading)
    val uiState: StateFlow<AccountSetupUiState> = _uiState.asStateFlow()

    init {
        attempt()
    }

    fun onRetry() {
        if (_uiState.value == AccountSetupUiState.Loading) return
        attempt()
    }

    fun onSignOut() {
        viewModelScope.launch { sessionManager.signOut() }
    }

    private fun attempt() {
        _uiState.value = AccountSetupUiState.Loading
        viewModelScope.launch {
            val pending = prefs.pendingSignup()
            if (pending == null) {
                // InitFailed khi mở app (mất mạng, chưa có token đã lưu): đọc lại phiên.
                sessionManager.refresh(forceTokenRefresh = true)
                (sessionManager.sessionState.value as? SessionState.InitFailed)?.let {
                    _uiState.value = AccountSetupUiState.Failed(it.error)
                }
                return@launch
            }
            when (val result = completeSignup(pending.name, termsAccepted = true)) {
                is CompleteSignupResult.Success -> Unit
                is CompleteSignupResult.Failure -> when (result.error) {
                    // SessionManager tự đăng xuất và mở thông báo khóa.
                    AuthError.AccountLocked -> Unit
                    AuthError.Network -> _uiState.value = AccountSetupUiState.Failed(AppError.Network)
                    AuthError.TooManyAttempts -> _uiState.value = AccountSetupUiState.Failed(AppError.RateLimited)
                    else -> _uiState.value = AccountSetupUiState.Failed(AppError.Unknown((result.error as? AuthError.Unknown)?.cause))
                }
                // Họ tên đã qua NameRule và luôn gửi termsAccepted = true, nên không xảy ra.
                CompleteSignupResult.InvalidDisplayName, CompleteSignupResult.TermsRequired ->
                    _uiState.value = AccountSetupUiState.Failed(AppError.Unknown(null))
            }
        }
    }
}

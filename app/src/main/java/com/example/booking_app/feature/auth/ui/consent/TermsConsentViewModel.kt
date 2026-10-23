package com.example.booking_app.feature.auth.ui.consent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.ConsentPrefill
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.usecase.CompleteSignupResult
import com.example.booking_app.feature.auth.domain.usecase.CompleteSignupUseCase
import com.example.booking_app.feature.auth.domain.validation.NameRule
import com.example.booking_app.feature.auth.domain.validation.Validated
import com.example.booking_app.feature.auth.domain.validation.ValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TermsConsentUiState(
    val prefill: ConsentPrefill? = null,
    val name: String = "",
    val nameError: ValidationError? = null,
    val termsChecked: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = termsChecked && !isSubmitting
}

sealed interface TermsConsentEvent {
    data object ShowNetworkError : TermsConsentEvent

    data class ShowError(val error: AuthError) : TermsConsentEvent
}

/**
 * Bước Hoàn tất tài khoản sau Google/OTP lần đầu (FR-003, research R4). Xong thì `SessionManager` chuyển
 * sang `SignedIn` và `MainActivity` điều hướng; ở đây chỉ xử lý lỗi.
 */
@HiltViewModel
class TermsConsentViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val completeSignup: CompleteSignupUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TermsConsentUiState(
            name = savedStateHandle[KEY_NAME] ?: "",
            termsChecked = savedStateHandle[KEY_TERMS] ?: false,
        ),
    )
    val uiState: StateFlow<TermsConsentUiState> = _uiState.asStateFlow()

    private val _events = Channel<TermsConsentEvent>(Channel.BUFFERED)
    val events: Flow<TermsConsentEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val prefill = sessionManager.sessionState.filterIsInstance<SessionState.NeedsConsent>().first().prefill
            _uiState.update { state ->
                // Họ tên lấy từ Google chỉ điền lần đầu; người dùng đã sửa thì giữ bản đã sửa.
                val name = if (savedStateHandle.contains(KEY_NAME)) state.name else prefill.displayName.orEmpty()
                state.copy(prefill = prefill, name = name)
            }
        }
    }

    fun onNameChanged(name: String) {
        savedStateHandle[KEY_NAME] = name
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onTermsChecked(checked: Boolean) {
        savedStateHandle[KEY_TERMS] = checked
        _uiState.update { it.copy(termsChecked = checked) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val name = when (val v = NameRule.validate(state.name)) {
            is Validated.Invalid -> {
                _uiState.update { it.copy(nameError = v.error) }
                return
            }
            is Validated.Valid -> v.value
        }
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = completeSignup(name, termsAccepted = true)
            _uiState.update { it.copy(isSubmitting = false) }
            when (result) {
                is CompleteSignupResult.Success -> Unit
                CompleteSignupResult.InvalidDisplayName ->
                    _uiState.update { it.copy(nameError = ValidationError.NAME_LENGTH) }
                // Không xảy ra vì luôn gửi termsAccepted = true.
                CompleteSignupResult.TermsRequired -> _uiState.update { it.copy(termsChecked = false) }
                is CompleteSignupResult.Failure -> when (result.error) {
                    AuthError.Network -> _events.send(TermsConsentEvent.ShowNetworkError)
                    // SessionManager tự đăng xuất và mở thông báo khóa.
                    AuthError.AccountLocked -> Unit
                    else -> _events.send(TermsConsentEvent.ShowError(result.error))
                }
            }
        }
    }

    /** "Dùng tài khoản khác": đăng xuất, `MainActivity` quay về màn hình đăng nhập. */
    fun onUseOtherAccount() {
        viewModelScope.launch { sessionManager.signOut() }
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_TERMS = "termsChecked"
    }
}

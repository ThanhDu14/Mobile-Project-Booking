package com.example.booking_app.feature.auth.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WelcomeEvent {
    data object OpenPlayerHome : WelcomeEvent

    data object OpenLogin : WelcomeEvent

    // TODO(US1 T042): thay bằng OpenRegister khi có màn hình đăng ký.
    data object NotReady : WelcomeEvent
}

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val prefs: SessionPrefs,
) : ViewModel() {

    private val _events = Channel<WelcomeEvent>(Channel.BUFFERED)
    val events: Flow<WelcomeEvent> = _events.receiveAsFlow()

    /** "Xem sân trước" (FR-025): lần sau mở app vào thẳng trang chủ ở chế độ khách. */
    fun onBrowseAsGuest() = markSeenThen(WelcomeEvent.OpenPlayerHome)

    /** Bấm "Tạo tài khoản" hoặc "Đăng nhập" cũng tính là đã qua màn hình chào (research R15). */
    fun onCreateAccount() = markSeenThen(WelcomeEvent.NotReady)

    fun onLogin() = markSeenThen(WelcomeEvent.OpenLogin)

    private fun markSeenThen(event: WelcomeEvent) {
        viewModelScope.launch {
            prefs.setWelcomeSeen()
            _events.send(event)
        }
    }
}

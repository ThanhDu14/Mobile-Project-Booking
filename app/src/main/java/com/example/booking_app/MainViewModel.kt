package com.example.booking_app

import androidx.lifecycle.ViewModel
import com.example.booking_app.core.session.AppMode
import com.example.booking_app.core.session.AppModeController
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    sessionManager: SessionManager,
    private val prefs: SessionPrefs,
    private val appModeController: AppModeController,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    /** [011] Đổi chế độ chủ sân cũng phải điều hướng lại (research R7). */
    val mode: StateFlow<AppMode> = appModeController.mode

    fun modeFor(state: SessionState): AppMode = appModeController.modeFor(state)

    /** Graph gốc của vai trò đã điều hướng tới gần nhất; chỉ điều hướng lại khi graph đích đổi. */
    var lastRootGraph: Int? = null

    /** Trạng thái đã xử lý gần nhất. Giữ qua xoay màn hình để chỉ điều hướng khi trạng thái đổi thật. */
    var lastRoutedState: SessionState? = null

    suspend fun welcomeSeen(): Boolean = prefs.welcomeSeen.first()
}

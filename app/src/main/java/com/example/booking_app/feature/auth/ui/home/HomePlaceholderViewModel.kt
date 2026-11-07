package com.example.booking_app.feature.auth.ui.home

import androidx.lifecycle.ViewModel
import com.example.booking_app.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomePlaceholderViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {
    val isGuest: StateFlow<Boolean> = sessionManager.isGuest
}

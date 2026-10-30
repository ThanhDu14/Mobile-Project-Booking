package com.example.booking_app.feature.auth.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.account.ui.account.initialsOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OwnerHomePlaceholderViewModel @Inject constructor(
    profileReader: UserProfileReader,
) : ViewModel() {
    /** Chữ cái đầu cho avatar ở header; rỗng khi chưa tải được hồ sơ. */
    val initials: StateFlow<String> = profileReader.observeMyProfile()
        .map { profile -> profile?.displayName?.let(::initialsOf).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}

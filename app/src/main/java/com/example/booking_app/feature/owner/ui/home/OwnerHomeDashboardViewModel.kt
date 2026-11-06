package com.example.booking_app.feature.owner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.account.ui.account.initialsOf
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OwnerHomeDashboardState(
    val venues: List<OwnerVenue> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
)

@HiltViewModel
class OwnerHomeDashboardViewModel @Inject constructor(
    profileReader: UserProfileReader,
    private val venueRepository: OwnerVenueRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OwnerHomeDashboardState(isLoading = true))
    val state: StateFlow<OwnerHomeDashboardState> = _state.asStateFlow()

    val initials: StateFlow<String> = profileReader.observeMyProfile()
        .map { profile -> profile?.displayName?.let(::initialsOf).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        venueRepository.getOwnedVenues()
            .onSuccess { venues ->
                _state.value = OwnerHomeDashboardState(
                    venues = venues,
                    isLoading = false,
                    isEmpty = venues.isEmpty(),
                )
            }
            .onFailure { failure ->
                _state.value = OwnerHomeDashboardState(
                    isLoading = false,
                    error = failure.message ?: "Không tải được thông tin cơ sở",
                )
            }
    }
}

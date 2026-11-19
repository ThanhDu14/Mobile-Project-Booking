package com.example.booking_app.feature.community.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.Attendee
import com.example.booking_app.feature.community.domain.toCommunityError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EventAttendeesUiState {
    data object Loading : EventAttendeesUiState
    data class Content(val attendees: List<Attendee>) : EventAttendeesUiState
    data object Empty : EventAttendeesUiState
    data class Error(val cause: CommunityError) : EventAttendeesUiState
}

/** T056: danh sách người đã tham gia sự kiện (realtime, 4 trạng thái). */
@HiltViewModel
class EventAttendeesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val posts: PostRepository,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "Thiếu groupId" }
    val postId: String = checkNotNull(savedStateHandle[ARG_POST_ID]) { "Thiếu postId" }

    private val _state = MutableStateFlow<EventAttendeesUiState>(EventAttendeesUiState.Loading)
    val state: StateFlow<EventAttendeesUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        _state.value = EventAttendeesUiState.Loading
        start()
    }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            posts.observeAttendees(groupId, postId)
                .catch { _state.value = EventAttendeesUiState.Error(it.toCommunityError()) }
                .collect { list ->
                    _state.value = if (list.isEmpty()) {
                        EventAttendeesUiState.Empty
                    } else {
                        EventAttendeesUiState.Content(list)
                    }
                }
        }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        const val ARG_POST_ID = "postId"
    }
}

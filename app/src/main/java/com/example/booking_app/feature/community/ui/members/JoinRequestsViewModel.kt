package com.example.booking_app.feature.community.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.JoinRequest
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.ReviewJoinRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface JoinRequestsUiState {
    data object Loading : JoinRequestsUiState
    data class Content(val items: List<JoinRequest>) : JoinRequestsUiState
    data object Empty : JoinRequestsUiState
    data class Error(val cause: CommunityError) : JoinRequestsUiState
}

sealed interface JoinRequestsEvent {
    data class ShowError(val error: CommunityError) : JoinRequestsEvent
}

/**
 * T039: yêu cầu tham gia của một nhóm (chủ nhóm, quản trị viên). Duyệt/từ chối lạc quan: dòng biến mất ngay, lỗi thì
 * hiện lại đúng chỗ cũ kèm thông báo. `REQUEST_NOT_FOUND` (người khác đã xử lý, hoặc người xin đã hủy) thì không hoàn lại.
 */
@HiltViewModel
class JoinRequestsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val review: ReviewJoinRequest,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "Thiếu groupId" }

    private val _state = MutableStateFlow<JoinRequestsUiState>(JoinRequestsUiState.Loading)
    val state: StateFlow<JoinRequestsUiState> = _state.asStateFlow()

    private val _events = Channel<JoinRequestsEvent>(Channel.BUFFERED)
    val events: Flow<JoinRequestsEvent> = _events.receiveAsFlow()

    /** Danh sách mới nhất từ Firestore (cũ nhất trước). */
    private var source: List<JoinRequest> = emptyList()

    /** Người đã duyệt/từ chối lạc quan và chưa có phản hồi hoặc chưa biến mất khỏi [source]. */
    private var hidden: Set<String> = emptySet()
    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        _state.value = JoinRequestsUiState.Loading
        start()
    }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            groups.observeJoinRequests(groupId)
                .catch { _state.value = JoinRequestsUiState.Error(it.toCommunityError()) }
                .collect {
                    source = it
                    val ids = it.mapTo(HashSet()) { r -> r.userId }
                    hidden = hidden.filterTo(HashSet()) { id -> id in ids }
                    publish()
                }
        }
    }

    fun onApprove(userId: String) = review(userId, "approve") { id -> review.approve(groupId, userId, id) }

    fun onReject(userId: String) = review(userId, "reject") { id -> review.reject(groupId, userId, id) }

    private fun review(userId: String, action: String, call: suspend (String) -> Result<Unit>) {
        if (userId in hidden) return
        hidden = hidden + userId
        publish()
        val key = "join_${action}_${userId}_${source.firstOrNull { it.userId == userId }?.createdAt}"
        viewModelScope.launch {
            val result = try {
                val requestId = savedStateHandle.get<String>(key)
                    ?: UUID.randomUUID().toString().also { savedStateHandle[key] = it }
                call(requestId)
            } catch (e: CancellationException) {
                throw e
            }
            result.onSuccess { savedStateHandle.remove<String>(key) }
            result.onFailure {
                val error = it.toCommunityError()
                if (error !is CommunityError.Network && error !is CommunityError.Busy) {
                    savedStateHandle.remove<String>(key)
                }
                // Yêu cầu không còn: giữ ẩn, danh sách realtime sẽ phản ánh.
                if (error !is CommunityError.RequestNotFound) hidden = hidden - userId
                publish()
                _events.trySend(JoinRequestsEvent.ShowError(error))
            }
        }
    }

    private fun publish() {
        if (_state.value is JoinRequestsUiState.Error && source.isEmpty() && hidden.isEmpty()) return
        val items = source.filter { it.userId !in hidden }
        _state.value = if (items.isEmpty()) JoinRequestsUiState.Empty else JoinRequestsUiState.Content(items)
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
    }
}

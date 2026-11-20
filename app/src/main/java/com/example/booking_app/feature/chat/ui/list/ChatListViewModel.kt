package com.example.booking_app.feature.chat.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.settledUid
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatMode
import com.example.booking_app.feature.chat.domain.model.ChatStatus
import com.example.booking_app.feature.chat.domain.model.ChatSummary
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.domain.toChatError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed interface ChatListEvent {
    data class OpenChat(val chatId: String) : ChatListEvent
    data class RequireLogin(val returnTo: String) : ChatListEvent
    data object OpenJoinGroup : ChatListEvent
    data object OpenFindVenue : ChatListEvent
}

/**
 * ViewModel danh sách cuộc trò chuyện cho cả chế độ `PLAYER` ("Tin nhắn") và `OWNER` ("Tin nhắn khách hàng").
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val mode: ChatMode = when (savedStateHandle.get<String>(ARG_MODE)?.uppercase()) {
        ChatMode.OWNER.name -> ChatMode.OWNER
        else -> ChatMode.PLAYER
    }

    val currentUid: String? get() = sessionManager.currentUid()

    private val _state = MutableStateFlow<UiState<List<ChatSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ChatSummary>>> = _state.asStateFlow()

    private val _events = Channel<ChatListEvent>(Channel.BUFFERED)
    val events: Flow<ChatListEvent> = _events.receiveAsFlow()

    private var observeJob: Job? = null
    private var settled = false
    private var uid: String? = null
    private var active = true

    init {
        // L4/M2: chỉ quyết định khi phiên đã chốt; đăng nhập xong quay về thì tự nạp lại.
        viewModelScope.launch {
            sessionManager.settledUid().collect { newUid ->
                settled = true
                uid = newUid
                // Đổi tài khoản: không hiện danh sách của tài khoản trước.
                if (_state.value is UiState.Content) _state.value = UiState.Loading
                load()
            }
        }
    }

    fun retry() {
        load()
    }

    /** M3: màn hình ẩn (onStop, hoặc mục "Tin nhắn" bị ẩn trong Cộng đồng) thì gỡ listener Firestore. */
    fun setActive(isActive: Boolean) {
        active = isActive
        if (!isActive) {
            observeJob?.cancel()
            observeJob = null
        } else if (observeJob == null && uid != null) {
            load(promptLogin = false)
        }
    }

    private fun load(promptLogin: Boolean = true) {
        observeJob?.cancel()
        observeJob = null
        if (!settled) {
            _state.value = UiState.Loading
            return
        }
        val uid = uid
        if (uid == null) {
            _state.value = UiState.Error(ChatError.Unauthenticated().code, ::retry)
            val returnTo = if (mode == ChatMode.OWNER) "smashnow://chat/owner" else "smashnow://chat"
            if (promptLogin) _events.trySend(ChatListEvent.RequireLogin(returnTo))
            return
        }
        if (!active) return

        if (_state.value !is UiState.Content) _state.value = UiState.Loading
        observeJob = viewModelScope.launch {
            repository.observeChats(mode)
                .catch { throwable ->
                    val err = throwable.toChatError()
                    _state.value = UiState.Error(err.code, ::retry)
                }
                .collect { items ->
                    val visible = items
                        .filter { item ->
                            if (item.status == ChatStatus.DELETED) return@filter false
                            when (mode) {
                                ChatMode.OWNER -> item.ownerId == uid && item.type == ChatType.DIRECT
                                ChatMode.PLAYER -> item.ownerId != uid
                            }
                        }
                        .sortedByDescending { it.updatedAt ?: Instant.EPOCH }
                    _state.value = if (visible.isEmpty()) UiState.Empty else UiState.Content(visible)
                }
        }
    }

    fun onChatClicked(chatId: String) {
        _events.trySend(ChatListEvent.OpenChat(chatId))
    }

    fun onJoinGroupClicked() {
        _events.trySend(ChatListEvent.OpenJoinGroup)
    }

    fun onFindVenueClicked() {
        _events.trySend(ChatListEvent.OpenFindVenue)
    }

    companion object {
        const val ARG_MODE = "mode"
    }
}

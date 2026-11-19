package com.example.booking_app.feature.chat.ui.conversation

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.R
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.data.NetworkMonitor
import com.example.booking_app.feature.chat.data.di.ChatMediaSignedUrlCache
import com.example.booking_app.feature.chat.data.dto.toChatMessage
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatDetail
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.ChatMessageRules
import com.example.booking_app.feature.chat.domain.model.ChatStatus
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.domain.model.Delivery
import com.example.booking_app.feature.chat.domain.model.DiscardResult
import com.example.booking_app.feature.chat.domain.model.GroupHeaderInfo
import com.example.booking_app.feature.chat.domain.model.MessageStatus
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.example.booking_app.feature.chat.domain.model.VenueSummary
import com.example.booking_app.feature.chat.domain.toChatError
import com.example.booking_app.feature.chat.domain.usecase.DiscardMessage
import com.example.booking_app.feature.chat.domain.usecase.LoadOlderMessages
import com.example.booking_app.feature.chat.domain.usecase.MarkChatRead
import com.example.booking_app.feature.chat.domain.usecase.ObserveMessages
import com.example.booking_app.feature.chat.domain.usecase.OpenDirectChat
import com.example.booking_app.feature.chat.domain.usecase.RecallMessage
import com.example.booking_app.feature.chat.domain.usecase.RetryMessage
import com.example.booking_app.feature.chat.domain.usecase.SendMessage
import com.example.booking_app.feature.chat.domain.usecase.SetBlocked
import com.example.booking_app.feature.chat.domain.usecase.SetMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

enum class ComposerDisabledReason {
    READ_ONLY,
    GROUP_LOCKED,
    BLOCKED,
}

enum class MessageMenuAction {
    COPY,
    RECALL,
    REPORT,
}

data class ChatHeaderUi(
    val chatId: String,
    val type: ChatType,
    val title: String,
    val avatarPath: String? = null,
    val avatarBucket: String = "avatars",
    val isOwnerView: Boolean = false,
    val groupMemberCount: Int? = null,
    val venue: VenueSummary? = null,
    val muted: Boolean = false,
    val blockedOther: Boolean = false,
    val isDeletedOtherParty: Boolean = false,
)

data class ChatUiState(
    val header: ChatHeaderUi? = null,
    val messages: UiState<List<ChatMessage>> = UiState.Loading,
    val imageUrls: Map<String, String> = emptyMap(),
    val composerText: String = "",
    val pendingImageUri: String? = null,
    val disabledReason: ComposerDisabledReason? = null,
    val isOffline: Boolean = false,
    val loadingOlder: Boolean = false,
    val canLoadOlder: Boolean = true,
) {
    val rawCharCount: Int get() = ChatMessageRules.codePointLength(composerText)
    val isOverLimit: Boolean get() = rawCharCount > ChatMessageRules.MAX_TEXT_LENGTH
    val showCounter: Boolean get() = rawCharCount >= COUNTER_THRESHOLD || isOverLimit
    val canSend: Boolean get() = disabledReason == null
    val canSubmit: Boolean
        get() = canSend && !isOverLimit && (pendingImageUri != null || ChatMessageRules.isValidText(composerText))

    companion object {
        const val COUNTER_THRESHOLD = 800
    }
}

sealed interface ChatEvent {
    data class RequireLogin(val returnTo: String) : ChatEvent
    data object KickedOut : ChatEvent
    data class ShowError(val error: ChatError) : ChatEvent
    data class ShowMessage(@param:StringRes val resId: Int) : ChatEvent
    data class OpenPhoto(val url: String) : ChatEvent
    data class OpenVenue(val venueId: String) : ChatEvent
    data class OpenReport(val chatId: String, val messageId: String) : ChatEvent
    data object CameraPermissionDenied : ChatEvent
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val openDirectChat: OpenDirectChat,
    private val observeMessages: ObserveMessages,
    private val loadOlderMessages: LoadOlderMessages,
    private val sendMessage: SendMessage,
    private val retryMessage: RetryMessage,
    private val discardMessage: DiscardMessage,
    private val markChatRead: MarkChatRead,
    private val setMuted: SetMuted,
    private val setBlocked: SetBlocked,
    private val recallMessage: RecallMessage,
    @param:ChatMediaSignedUrlCache private val signedUrlCache: SignedUrlCache,
    private val networkMonitor: NetworkMonitor,
    private val sessionManager: SessionManager,
    private val clock: AppClock,
) : ViewModel() {

    private val argChatId: String? = savedStateHandle[ARG_CHAT_ID]
    private val argGroupId: String? = savedStateHandle[ARG_GROUP_ID]
    private val argVenueId: String? = savedStateHandle[ARG_VENUE_ID]

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChatEvent>(Channel.BUFFERED)
    val events: Flow<ChatEvent> = _events.receiveAsFlow()

    private var activeChatId: String? = null
    private var observeJob: Job? = null
    private var viewingJob: Job? = null
    private var screenVisible: Boolean = false
    private var kickedEmitted: Boolean = false
    private var blockedByOther: Boolean = false
    private var lastMarkedMarker: String? = null

    private val olderMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private var currentServerMessages: List<ChatMessage> = emptyList()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOffline = !online) }
            }
        }
        start()
    }

    fun retryLoad() {
        start()
    }

    private fun start() {
        val uid = sessionManager.currentUid()
        if (uid == null) {
            val returnTo = when {
                !argVenueId.isNullOrBlank() -> "smashnow://chat/direct?venueId=$argVenueId"
                !argGroupId.isNullOrBlank() -> "smashnow://chat/group/$argGroupId"
                !argChatId.isNullOrBlank() -> "smashnow://chat/$argChatId"
                else -> "smashnow://chat"
            }
            _uiState.update { it.copy(messages = UiState.Error(ChatError.Unauthenticated().code)) }
            _events.trySend(ChatEvent.RequireLogin(returnTo))
            return
        }

        when {
            !argVenueId.isNullOrBlank() -> openDirectAndObserve(argVenueId)
            !argGroupId.isNullOrBlank() -> startObserving("group_$argGroupId")
            !argChatId.isNullOrBlank() -> startObserving(argChatId)
            else -> _uiState.update { it.copy(messages = UiState.Error(ChatError.ChatNotFound().code)) }
        }
    }

    private fun openDirectAndObserve(venueId: String) {
        observeJob?.cancel()
        _uiState.update { it.copy(messages = UiState.Loading) }
        observeJob = viewModelScope.launch {
            val result = try {
                openDirectChat(venueId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.fold(
                onSuccess = { opened -> startObserving(opened.chatId) },
                onFailure = { throwable ->
                    val err = throwable.toChatError()
                    _uiState.update { it.copy(messages = UiState.Error(err.code, ::retryLoad)) }
                    _events.trySend(ChatEvent.ShowError(err))
                },
            )
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startObserving(chatId: String) {
        activeChatId = chatId
        lastMarkedMarker = null
        kickedEmitted = false
        blockedByOther = false
        olderMessages.value = emptyList()
        observeJob?.cancel()
        _uiState.update { it.copy(messages = UiState.Loading, canLoadOlder = true) }

        if (screenVisible) {
            startViewingHeartbeat(chatId)
        }

        val uid = sessionManager.currentUid().orEmpty()
        val chatFlow = repository.observeChat(chatId)
        val groupHeaderFlow = chatFlow.flatMapLatest { detail ->
            val gid = detail?.groupId
                ?: argGroupId
                ?: chatId.takeIf { it.startsWith("group_") }?.removePrefix("group_")
            if (detail?.type == ChatType.GROUP && !gid.isNullOrBlank()) {
                repository.observeGroupHeader(gid)
            } else {
                flowOf(null)
            }
        }
        val messagesFlow = observeMessages(chatId)
        val outboxFlow = repository.observeOutbox(chatId)

        observeJob = viewModelScope.launch {
            combine(chatFlow, groupHeaderFlow, messagesFlow, olderMessages, outboxFlow) { detail, groupHeader, live, older, outbox ->
                CombinedSnapshot(detail, groupHeader, live, older, outbox)
            }.catch { throwable ->
                val err = throwable.toChatError()
                if (err is ChatError.NotParticipant) {
                    emitKickedOutOnce()
                } else {
                    _uiState.update { it.copy(messages = UiState.Error(err.code, ::retryLoad)) }
                    _events.trySend(ChatEvent.ShowError(err))
                }
            }.collect { snap ->
                val detail = snap.detail
                if (detail == null) {
                    _uiState.update { it.copy(messages = UiState.Error(ChatError.ChatNotFound().code, ::retryLoad)) }
                    return@collect
                }

                // Kiểm tra lỗi đặc biệt từ outbox (BLOCKED, NOT_PARTICIPANT, GROUP_LOCKED, CHAT_READ_ONLY).
                handleOutboxErrors(snap.outbox)

                currentServerMessages = mergeServerMessages(snap.older, snap.live)
                val merged = mergeWithOutbox(currentServerMessages, snap.outbox, uid)
                val header = buildHeader(detail, snap.groupHeader, uid)
                val disabled = computeDisabledReason(detail, snap.groupHeader)

                _uiState.update { state ->
                    state.copy(
                        header = header,
                        messages = if (merged.isEmpty()) UiState.Empty else UiState.Content(merged),
                        disabledReason = disabled,
                        canLoadOlder = state.canLoadOlder && snap.live.size >= PAGE_SIZE,
                    )
                }

                resolveSignedImageUrls(merged)
            }
        }
    }

    private fun handleOutboxErrors(entries: List<OutboxEntry>) {
        if (entries.any { it.status == Delivery.FAILED && it.errorCode == "NOT_PARTICIPANT" }) {
            emitKickedOutOnce()
            return
        }
        if (entries.any { it.status == Delivery.FAILED && it.errorCode == "BLOCKED" }) {
            blockedByOther = true
        }
    }

    private fun emitKickedOutOnce() {
        if (kickedEmitted) return
        kickedEmitted = true
        viewingJob?.cancel()
        viewingJob = null
        _events.trySend(ChatEvent.KickedOut)
    }

    private fun mergeServerMessages(older: List<ChatMessage>, live: List<ChatMessage>): List<ChatMessage> {
        if (older.isEmpty()) return live
        val byId = LinkedHashMap<String, ChatMessage>(older.size + live.size)
        for (m in older) byId[m.id] = m
        for (m in live) byId[m.id] = m
        return byId.values.sortedBy { it.createdAt ?: Instant.EPOCH }
    }

    private fun mergeWithOutbox(
        server: List<ChatMessage>,
        outbox: List<OutboxEntry>,
        currentUid: String,
    ): List<ChatMessage> {
        val serverIds = server.mapTo(HashSet(server.size)) { it.id }
        val pending = outbox
            .filterNot { it.messageId in serverIds }
            .sortedBy { it.createdAtLocal }
            .map { it.toChatMessage(currentUid) }
        return server + pending
    }

    private fun buildHeader(detail: ChatDetail, groupHeader: GroupHeaderInfo?, currentUid: String): ChatHeaderUi {
        return when (detail.type) {
            ChatType.GROUP -> ChatHeaderUi(
                chatId = detail.id,
                type = ChatType.GROUP,
                title = groupHeader?.name.orEmpty(),
                avatarPath = groupHeader?.avatarPath,
                avatarBucket = "group-avatars",
                groupMemberCount = groupHeader?.memberCount ?: detail.memberIds.size,
                muted = detail.myParticipant.muted,
                blockedOther = false,
            )
            ChatType.DIRECT -> {
                val other = detail.otherParty
                ChatHeaderUi(
                    chatId = detail.id,
                    type = ChatType.DIRECT,
                    title = other?.displayName.orEmpty(),
                    avatarPath = other?.avatarPath,
                    avatarBucket = "avatars",
                    isOwnerView = detail.ownerId == currentUid,
                    venue = detail.venue,
                    muted = detail.myParticipant.muted,
                    blockedOther = detail.myParticipant.blockedOther,
                    isDeletedOtherParty = other?.isDeleted == true,
                )
            }
        }
    }

    private fun computeDisabledReason(detail: ChatDetail, groupHeader: GroupHeaderInfo?): ComposerDisabledReason? = when {
        detail.status == ChatStatus.READ_ONLY -> ComposerDisabledReason.READ_ONLY
        detail.type == ChatType.GROUP && groupHeader?.isLocked == true -> ComposerDisabledReason.GROUP_LOCKED
        blockedByOther -> ComposerDisabledReason.BLOCKED
        else -> null
    }

    private fun resolveSignedImageUrls(messages: List<ChatMessage>) {
        // Chỉ xin signed URL cho tin NORMAL có imagePath (không xin cho tin HIDDEN/RECALLED, FR-015).
        val paths = messages
            .filter { it.status == MessageStatus.NORMAL && !it.imagePath.isNullOrBlank() }
            .mapNotNull { it.imagePath }
            .distinct()
        val missing = paths.filter { it !in _uiState.value.imageUrls }
        if (missing.isEmpty()) return

        viewModelScope.launch {
            val resolved = signedUrlCache.resolve(missing)
            if (resolved.isNotEmpty()) {
                _uiState.update { it.copy(imageUrls = it.imageUrls + resolved) }
            }
        }
    }

    fun onComposerChanged(text: String) {
        _uiState.update { it.copy(composerText = text) }
    }

    fun onImageSelected(uri: String) {
        _uiState.update { it.copy(pendingImageUri = uri) }
    }

    fun onPendingImageCleared() {
        _uiState.update { it.copy(pendingImageUri = null) }
    }

    /** Người dùng từ chối quyền camera: báo giải thích, vẫn cho chọn ảnh từ Photo Picker (AC 4.5). */
    fun onCameraPermissionDenied() {
        _events.trySend(ChatEvent.CameraPermissionDenied)
    }

    fun onSendClicked() {
        val chatId = activeChatId ?: return
        val state = _uiState.value
        if (!state.canSubmit) return

        val text = state.composerText
        val imageUri = state.pendingImageUri
        _uiState.update { it.copy(composerText = "", pendingImageUri = null) }

        viewModelScope.launch {
            val result = try {
                sendMessage(chatId = chatId, rawText = text, localImageUri = imageUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.onFailure { throwable ->
                val err = throwable.toChatError()
                val invalidImage = err is ChatError.ImageTooLarge || err is ChatError.ImageUnreadable
                // Nếu ảnh lỗi / quá 2 MB thì bỏ ảnh chọn nhưng giữ nguyên chú thích đang gõ (AC 4.3).
                _uiState.update {
                    it.copy(
                        composerText = text,
                        pendingImageUri = if (invalidImage) null else imageUri,
                    )
                }
                _events.trySend(ChatEvent.ShowError(err))
            }
        }
    }

    fun onRetryMessage(messageId: String) {
        val chatId = activeChatId ?: return
        blockedByOther = false
        viewModelScope.launch {
            retryMessage(chatId, messageId).onFailure { err ->
                _events.trySend(ChatEvent.ShowError(err.toChatError()))
            }
        }
    }

    fun onDiscardMessage(messageId: String) {
        val chatId = activeChatId ?: return
        viewModelScope.launch {
            discardMessage(chatId, messageId).fold(
                onSuccess = { outcome ->
                    if (outcome == DiscardResult.ALREADY_SENT) {
                        _events.trySend(ChatEvent.ShowMessage(R.string.chat_already_sent_notice))
                    }
                },
                onFailure = { err ->
                    _events.trySend(ChatEvent.ShowError(err.toChatError()))
                },
            )
        }
    }

    fun onLatestMessageVisible() {
        val chatId = activeChatId ?: return
        val latestServerId = currentServerMessages.lastOrNull()?.id ?: "empty"
        val marker = "$chatId:$latestServerId"
        if (lastMarkedMarker == marker) return
        lastMarkedMarker = marker
        viewModelScope.launch {
            markChatRead(chatId)
        }
    }

    fun onScreenVisible() {
        screenVisible = true
        val chatId = activeChatId ?: return
        startViewingHeartbeat(chatId)
    }

    fun onScreenHidden() {
        screenVisible = false
        viewingJob?.cancel()
        viewingJob = null
        val chatId = activeChatId ?: return
        viewModelScope.launch {
            repository.setViewing(chatId, null)
        }
    }

    private fun startViewingHeartbeat(chatId: String) {
        viewingJob?.cancel()
        viewingJob = viewModelScope.launch {
            while (true) {
                val estimatedServerNowMs = clock.now() + repository.estimatedServerOffsetMs()
                repository.setViewing(chatId, Instant.ofEpochMilli(estimatedServerNowMs + VIEWING_TTL_MS))
                delay(VIEWING_RENEW_INTERVAL_MS)
            }
        }
    }

    fun loadOlder() {
        val chatId = activeChatId ?: return
        val state = _uiState.value
        if (state.loadingOlder || !state.canLoadOlder) return
        val oldestAt = currentServerMessages.firstOrNull { it.createdAt != null }?.createdAt ?: return

        _uiState.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val result = try {
                loadOlderMessages(chatId, oldestAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.fold(
                onSuccess = { loaded ->
                    olderMessages.update { existing -> mergeServerMessages(loaded, existing) }
                    _uiState.update {
                        it.copy(
                            loadingOlder = false,
                            canLoadOlder = loaded.size >= PAGE_SIZE,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update { it.copy(loadingOlder = false) }
                    _events.trySend(ChatEvent.ShowError(throwable.toChatError()))
                },
            )
        }
    }

    fun onPhotoClicked(message: ChatMessage) {
        if (message.status != MessageStatus.NORMAL) return
        val url = message.imagePath?.let { _uiState.value.imageUrls[it] } ?: message.localImageUri ?: return
        _events.trySend(ChatEvent.OpenPhoto(url))
    }

    fun onVenueClicked() {
        val venueId = _uiState.value.header?.venue?.id ?: return
        _events.trySend(ChatEvent.OpenVenue(venueId))
    }

    fun onToggleMuted() {
        val chatId = activeChatId ?: return
        val targetMuted = !(_uiState.value.header?.muted ?: false)
        viewModelScope.launch {
            setMuted(chatId, targetMuted).fold(
                onSuccess = {
                    _events.trySend(
                        ChatEvent.ShowMessage(
                            if (targetMuted) R.string.chat_muted_success else R.string.chat_unmuted_success,
                        ),
                    )
                },
                onFailure = { err -> _events.trySend(ChatEvent.ShowError(err.toChatError())) },
            )
        }
    }

    fun onToggleBlockedConfirmed() {
        val chatId = activeChatId ?: return
        val header = _uiState.value.header ?: return
        if (header.type != ChatType.DIRECT) return
        val targetBlocked = !header.blockedOther
        viewModelScope.launch {
            setBlocked(chatId, targetBlocked).fold(
                onSuccess = {
                    _events.trySend(
                        ChatEvent.ShowMessage(
                            if (targetBlocked) R.string.chat_blocked_success else R.string.chat_unblocked_success,
                        ),
                    )
                },
                onFailure = { err -> _events.trySend(ChatEvent.ShowError(err.toChatError())) },
            )
        }
    }

    /** Tính danh sách hành động khi giữ lâu vào một tin nhắn (T048, T052). */
    fun availableActionsFor(message: ChatMessage): List<MessageMenuAction> {
        if (message.delivery != Delivery.SENT || message.status != MessageStatus.NORMAL) return emptyList()
        val nowServer = Instant.ofEpochMilli(clock.now() + repository.estimatedServerOffsetMs())
        return buildList {
            if (message.text.isNotBlank()) add(MessageMenuAction.COPY)
            if (message.canRecall(nowServer)) add(MessageMenuAction.RECALL)
            if (message.canReport) add(MessageMenuAction.REPORT)
        }
    }

    fun onRecallConfirmed(messageId: String) {
        val chatId = activeChatId ?: return
        viewModelScope.launch {
            recallMessage(chatId, messageId).fold(
                onSuccess = { _events.trySend(ChatEvent.ShowMessage(R.string.chat_recalled_success)) },
                onFailure = { err -> _events.trySend(ChatEvent.ShowError(err.toChatError())) },
            )
        }
    }

    fun onReportMessageSelected(messageId: String) {
        val chatId = activeChatId ?: return
        _events.trySend(ChatEvent.OpenReport(chatId, messageId))
    }

    override fun onCleared() {
        viewingJob?.cancel()
        observeJob?.cancel()
        super.onCleared()
    }

    private data class CombinedSnapshot(
        val detail: ChatDetail?,
        val groupHeader: GroupHeaderInfo?,
        val live: List<ChatMessage>,
        val older: List<ChatMessage>,
        val outbox: List<OutboxEntry>,
    )

    companion object {
        const val ARG_CHAT_ID = "chatId"
        const val ARG_GROUP_ID = "groupId"
        const val ARG_VENUE_ID = "venueId"
        const val PAGE_SIZE = 30
        const val VIEWING_TTL_MS = 60_000L
        const val VIEWING_RENEW_INTERVAL_MS = 45_000L
    }
}

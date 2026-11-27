package com.example.booking_app.feature.notification.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.notification.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class InboxState(
    val loading: Boolean = true,
    val items: List<Notification> = emptyList(),
    val error: Boolean = false,
    val hasMore: Boolean = false,
    val busy: Boolean = false,
)

data class NotificationOpen(val item: Notification, val route: String?)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val session: NotificationSession,
    private val resolver: NotificationRouteResolver,
) : ViewModel() {
    private val mutableState = MutableStateFlow(InboxState())
    val state = mutableState.asStateFlow()
    private val messages = Channel<NotificationOpen>(Channel.BUFFERED)
    val open = messages.receiveAsFlow()
    private var cursor: NotificationCursor? = null
    private var job: Job? = null
    private var currentUid: String? = null
    private var retryAction: (() -> Unit)? = null
    init {
        viewModelScope.launch {
            session.uids.distinctUntilChanged().collect { uid ->
                job?.cancel()
                while (messages.tryReceive().isSuccess) { /* Drop events from previous account. */ }
                currentUid = uid
                retryAction = null
                cursor = null
                mutableState.value = InboxState(loading = uid != null)
                if (uid != null) refresh()
            }
        }
    }
    fun refresh() = load(false)
    fun retry() { retryAction?.invoke() ?: refresh() }
    fun more() { if (mutableState.value.hasMore) load(true) }
    private fun load(append: Boolean) {
        if (currentUid == null || mutableState.value.busy || (append && mutableState.value.loading)) return
        job?.cancel()
        job = viewModelScope.launch {
            val previous = mutableState.value
            mutableState.value = previous.copy(loading = !append, busy = true, error = false)
            try {
                val page = repository.loadPage(if (append) cursor else null)
                cursor = page.nextCursor
                retryAction = null
                mutableState.value = InboxState(loading = false, items = (if (append) previous.items else emptyList()) + page.items, hasMore = cursor != null)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { retryAction = { load(append) }; mutableState.value = previous.copy(loading = false, busy = false, error = true) }
        }
    }
    fun markAll() = read(null)
    fun tap(item: Notification) = read(item)
    fun openPush(recipientUid: String, id: String) {
        if (recipientUid != currentUid || id.isBlank() || '/' in id || mutableState.value.busy) return
        val expected = currentUid
        job = viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            try {
                val item = repository.loadById(id)
                if (currentUid != expected) return@launch
                mutableState.update { it.copy(busy = false) }
                if (item != null) read(item)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                retryAction = { openPush(recipientUid, id) }
                mutableState.update { it.copy(busy = false, error = true) }
            }
        }
    }
    private fun read(item: Notification?) {
        if (mutableState.value.busy || currentUid == null) return
        job = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = false) }
            try {
                if (item == null) repository.markAllRead() else repository.markRead(item.id)
                retryAction = null
                val route = item?.let { resolver.resolve(it) }
                mutableState.update { state -> state.copy(busy = false, items = state.items.map { if (item == null || item.id == it.id) it.copy(isRead = true) else it }) }
                if (item != null) messages.send(NotificationOpen(item, route))
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { retryAction = { read(item) }; mutableState.update { it.copy(busy = false, error = true) } }
        }
    }
}

data class PreferenceState(
    val loading: Boolean = true,
    val values: Map<NotificationType, Boolean> = emptyMap(),
    val busy: Boolean = false,
    val error: Boolean = false,
    val readOnly: Boolean = false,
)
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationPreferencesRepository,
    session: NotificationSession,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PreferenceState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var uid: String? = null
    private var retryWrite: Pair<Set<NotificationType>, Boolean>? = null
    init {
        viewModelScope.launch {
            session.uids.distinctUntilChanged().collect {
                job?.cancel(); uid = it; retryWrite = null
                mutableState.value = PreferenceState(loading = it != null)
                if (it != null) load()
            }
        }
    }
    fun retry() { retryWrite?.let { save(it.first, it.second) } ?: load() }
    fun load() {
        if (uid == null || mutableState.value.busy) return
        job = viewModelScope.launch {
            mutableState.value = PreferenceState()
            try {
                val snapshot = repository.loadSnapshot()
                mutableState.value = PreferenceState(loading = false, values = snapshot.values, error = snapshot.fromCache, readOnly = snapshot.fromCache)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.value = PreferenceState(loading = false, error = true) }
        }
    }
    fun toggle(types: Set<NotificationType>) {
        if (NotificationType.BOOKING_NEW in types || mutableState.value.values.isEmpty() || mutableState.value.readOnly) return
        save(types, NotificationPreferences.groupState(types, mutableState.value.values) != GroupState.ON)
    }
    private fun save(types: Set<NotificationType>, enabled: Boolean) {
        if (uid == null || mutableState.value.busy || mutableState.value.readOnly) return
        job = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = false) }
            try {
                repository.setEnabled(types, enabled)
                retryWrite = null
                mutableState.update { it.copy(busy = false, values = it.values + types.associateWith { enabled }) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { retryWrite = types to enabled; mutableState.update { it.copy(busy = false, error = true) } }
        }
    }
}

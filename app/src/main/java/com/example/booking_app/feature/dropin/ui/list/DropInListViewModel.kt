package com.example.booking_app.feature.dropin.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.FilterChip
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.usecase.GetDefaultDropInFilter
import com.example.booking_app.feature.dropin.domain.usecase.LoadDropInPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trạng thái danh sách buổi (contracts/ui-screens.md mục 2). Không dùng `core.ui.UiState` vì màn hình trống cần
 * biết có đang lọc hay không ([Empty.hasFilter]) để hiện nút "Bỏ bớt bộ lọc". Chip bộ lọc lấy từ
 * [DropInListViewModel.filter] để vẫn hiện được khi danh sách rỗng hoặc lỗi.
 */
sealed interface DropInListUiState {
    data object Loading : DropInListUiState

    data class Content(
        val items: List<DropInSession>,
        val canLoadMore: Boolean,
        val loadingMore: Boolean = false,
    ) : DropInListUiState

    data class Empty(val hasFilter: Boolean) : DropInListUiState

    data class Error(val cause: Throwable) : DropInListUiState
}

sealed interface DropInListEvent {
    data class OpenDetail(val sessionId: String) : DropInListEvent

    /** Tải trang tiếp lỗi: giữ danh sách đang hiện, Fragment báo bằng Snackbar. */
    data class LoadMoreFailed(val cause: Throwable) : DropInListEvent
}

/**
 * Danh sách buổi vãng lai (US1, T024). Bộ lọc ban đầu theo thứ tự ưu tiên: bộ lọc đã lưu trong [SavedStateHandle]
 * (xoay màn hình, process death) → Safe Args `filter` từ AI Assistant (AC 1.8) → bộ lọc mặc định từ hồ sơ (AC 1.2).
 * Mỗi trang [com.example.booking_app.feature.dropin.domain.model.Page.PAGE_SIZE] buổi.
 */
@HiltViewModel
class DropInListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val loadPage: LoadDropInPage,
    private val getDefaultFilter: GetDefaultDropInFilter,
) : ViewModel() {

    private val _filter = MutableStateFlow(DropInFilter())
    val filter: StateFlow<DropInFilter> = _filter.asStateFlow()

    private val _uiState = MutableStateFlow<DropInListUiState>(DropInListUiState.Loading)
    val uiState: StateFlow<DropInListUiState> = _uiState.asStateFlow()

    private val _events = Channel<DropInListEvent>(Channel.BUFFERED)
    val events: Flow<DropInListEvent> = _events.receiveAsFlow()

    private var cursor: DropInCursor? = null
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadJob = viewModelScope.launch {
            val initial = savedStateHandle.get<DropInFilterArgs>(KEY_CURRENT_FILTER)?.toFilter()
                ?: savedStateHandle.get<DropInFilterArgs>(ARG_FILTER)?.toFilter()
                ?: defaultFilter()
            setFilter(initial)
            loadFirstPage()
        }
    }

    fun retry() = reload()

    /** Từ bottom sheet bộ lọc (nút "Áp dụng"). */
    fun applyFilter(args: DropInFilterArgs) = applyFilter(args.toFilter())

    fun applyFilter(newFilter: DropInFilter) {
        setFilter(newFilter)
        reload()
    }

    fun onChipRemoved(chip: FilterChip) = applyFilter(_filter.value.without(chip))

    fun clearFilter() = applyFilter(DropInFilter())

    fun onSessionClicked(sessionId: String) {
        _events.trySend(DropInListEvent.OpenDetail(sessionId))
    }

    /** Gọi khi cuộn gần tới cuối danh sách. */
    fun loadMore() {
        val content = _uiState.value as? DropInListUiState.Content ?: return
        val next = cursor
        if (!content.canLoadMore || next == null || loadMoreJob?.isActive == true || loadJob?.isActive == true) return
        _uiState.value = content.copy(loadingMore = true)
        val requested = _filter.value
        loadMoreJob = viewModelScope.launch {
            val result = safeLoad(requested, next)
            val current = _uiState.value as? DropInListUiState.Content ?: return@launch
            result
                .onSuccess { page ->
                    cursor = page.next
                    val merged = (current.items + page.items).distinctBy { it.id }.sortedBy { it.startAt }
                    _uiState.value = DropInListUiState.Content(merged, canLoadMore = page.canLoadMore)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = current.copy(loadingMore = false)
                    _events.send(DropInListEvent.LoadMoreFailed(error))
                }
        }
    }

    private fun reload() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        loadJob = viewModelScope.launch { loadFirstPage() }
    }

    private suspend fun loadFirstPage() {
        _uiState.value = DropInListUiState.Loading
        cursor = null
        val requested = _filter.value
        safeLoad(requested, null)
            .onSuccess { page ->
                cursor = page.next
                val items = page.items.distinctBy { it.id }.sortedBy { it.startAt }
                _uiState.value = if (items.isEmpty()) {
                    DropInListUiState.Empty(hasFilter = !requested.isEmpty)
                } else {
                    DropInListUiState.Content(items, canLoadMore = page.canLoadMore)
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.value = DropInListUiState.Error(error)
            }
    }

    /** Usecase trả `Result`; phòng khi nó vẫn ném lỗi thì gói lại để không làm sập màn hình. */
    private suspend fun safeLoad(filter: DropInFilter, cursor: DropInCursor?): Result<Page<DropInSession>> =
        try {
            loadPage(filter, cursor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun defaultFilter(): DropInFilter =
        try {
            getDefaultFilter()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Không đọc được hồ sơ: vẫn mở danh sách không lọc.
            DropInFilter()
        }

    private fun setFilter(value: DropInFilter) {
        _filter.value = value
        savedStateHandle[KEY_CURRENT_FILTER] = DropInFilterArgs.from(value)
    }

    companion object {
        /** Tên Safe Args trong `nav_dropin.xml`. */
        const val ARG_FILTER = "filter"
        internal const val KEY_CURRENT_FILTER = "dropin_current_filter"
    }
}

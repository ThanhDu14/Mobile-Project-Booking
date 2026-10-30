package com.example.booking_app.feature.review.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.feature.review.domain.FavoriteRepository
import com.example.booking_app.feature.review.domain.model.FavoriteVenue
import com.example.booking_app.feature.review.domain.usecase.FavoriteToggle
import com.example.booking_app.feature.review.domain.usecase.ToggleFavoriteUseCase
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
import javax.inject.Inject

sealed interface FavoriteListEvent {
    /** Bỏ yêu thích không đồng bộ được, sân đã quay lại danh sách (FR-021). */
    data object SyncFailed : FavoriteListEvent
}

/**
 * Màn hình "Sân yêu thích" (US3). Danh sách lắng nghe realtime, mỗi lần tải thêm 20 sân khi cuộn
 * tới cuối. Bỏ yêu thích ẩn sân ngay, ghi lỗi thì hiện lại.
 */
@HiltViewModel
class FavoriteVenuesViewModel @Inject constructor(
    private val repository: FavoriteRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<FavoriteVenue>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<FavoriteVenue>>> = _uiState.asStateFlow()

    private val _events = Channel<FavoriteListEvent>(Channel.BUFFERED)
    val events: Flow<FavoriteListEvent> = _events.receiveAsFlow()

    private var limit = FavoriteRepository.PAGE_SIZE
    private var serverList: List<FavoriteVenue> = emptyList()

    /** Sân vừa bấm bỏ yêu thích nhưng ghi chưa xong (có thể đang offline). */
    private val pendingRemovals = mutableSetOf<String>()
    private var observeJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        _uiState.value = UiState.Loading
        observe()
    }

    /** Gọi khi cuộn tới cuối. Trang hiện tại chưa đầy nghĩa là đã hết sân. */
    fun loadMore() {
        if (_uiState.value !is UiState.Content || serverList.size < limit) return
        limit += FavoriteRepository.PAGE_SIZE
        observe()
    }

    fun unfavorite(venue: FavoriteVenue) {
        viewModelScope.launch {
            toggleFavorite(venue.toSummary(), isFavorite = true).collect { result ->
                when (result) {
                    is FavoriteToggle.Changed -> pendingRemovals += venue.venueId
                    is FavoriteToggle.Reverted -> {
                        pendingRemovals -= venue.venueId
                        _events.send(FavoriteListEvent.SyncFailed)
                    }
                }
                publish()
            }
            pendingRemovals -= venue.venueId
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeFavorites(limit)
                .catch { error -> _uiState.value = UiState.Error(error.message.orEmpty(), ::retry) }
                .collect { list ->
                    serverList = list
                    publish()
                }
        }
    }

    private fun publish() {
        val visible = serverList.filterNot { it.venueId in pendingRemovals }
        _uiState.value = if (visible.isEmpty()) UiState.Empty else UiState.Content(visible)
    }

    private fun FavoriteVenue.toSummary() = VenueSummary(venueId, name, photoUrl, address)
}

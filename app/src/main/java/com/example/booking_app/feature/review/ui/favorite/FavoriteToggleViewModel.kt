package com.example.booking_app.feature.review.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.review.domain.FavoriteRepository
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

sealed interface FavoriteToggleEvent {
    /** "Đã thêm vào yêu thích" (US3, kịch bản 1). */
    data object Added : FavoriteToggleEvent
    data object Removed : FavoriteToggleEvent
    data object SyncFailed : FavoriteToggleEvent

    /** Khách bấm trái tim: màn hình chứa nút mời đăng nhập. */
    data object LoginRequired : FavoriteToggleEvent
}

/**
 * Trạng thái của một [FavoriteToggleButton]. Màn hình chứa nút gọi [bind] khi đã có thông tin sân,
 * rồi gắn [onToggle] vào nút.
 */
@HiltViewModel
class FavoriteToggleViewModel @Inject constructor(
    private val repository: FavoriteRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _events = Channel<FavoriteToggleEvent>(Channel.BUFFERED)
    val events: Flow<FavoriteToggleEvent> = _events.receiveAsFlow()

    private var venue: VenueSummary? = null
    private var observeJob: Job? = null

    fun bind(venue: VenueSummary) {
        if (this.venue?.venueId == venue.venueId) {
            this.venue = venue
            return
        }
        this.venue = venue
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeIsFavorite(venue.venueId)
                .catch { /* Mất quyền đọc (vừa đăng xuất): giữ trạng thái đang hiện. */ }
                .collect { _isFavorite.value = it }
        }
    }

    fun onToggle() {
        val current = venue ?: return
        if (sessionManager.currentUid() == null) {
            _events.trySend(FavoriteToggleEvent.LoginRequired)
            return
        }
        viewModelScope.launch {
            toggleFavorite(current, _isFavorite.value).collect { result ->
                when (result) {
                    is FavoriteToggle.Changed -> {
                        _isFavorite.value = result.isFavorite
                        _events.send(if (result.isFavorite) FavoriteToggleEvent.Added else FavoriteToggleEvent.Removed)
                    }
                    is FavoriteToggle.Reverted -> {
                        _isFavorite.value = result.isFavorite
                        _events.send(FavoriteToggleEvent.SyncFailed)
                    }
                }
            }
        }
    }
}

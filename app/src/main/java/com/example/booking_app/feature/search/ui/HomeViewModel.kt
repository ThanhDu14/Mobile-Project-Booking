package com.example.booking_app.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.feature.search.domain.HomeVenueRepository
import com.example.booking_app.feature.search.domain.HomeVenueSorter
import com.example.booking_app.feature.search.domain.model.HomeSort
import com.example.booking_app.feature.search.domain.model.HomeVenue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(val items: List<HomeVenue>, val total: Int, val sort: HomeSort, val canLoadMore: Boolean) :
        HomeUiState

    data object Empty : HomeUiState

    data class Error(val error: AppError) : HomeUiState
}

/**
 * Trang chủ (spec 020 US5, FR-029): "Tất cả sân · N", sắp xếp, tải thêm 20 sân mỗi lần cuộn tới cuối (FR-016).
 * Đọc một lần rồi sắp xếp, chia trang trên máy; đổi cách sắp xếp không đọc lại. ViewModel sống cùng Fragment trên
 * back stack nên quay lại từ chi tiết sân giữ nguyên cách sắp xếp và số trang (FR-033).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeVenueRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var all: List<HomeVenue> = emptyList()
    private var sort = HomeSort.RATING
    private var shownCount = PAGE_SIZE
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onSortSelected(newSort: HomeSort) {
        if (newSort == sort) return
        sort = newSort
        shownCount = PAGE_SIZE
        all = HomeVenueSorter.sort(all, sort)
        publish()
    }

    fun onLoadMore() {
        val state = _uiState.value as? HomeUiState.Content ?: return
        if (!state.canLoadMore) return
        shownCount += PAGE_SIZE
        publish()
    }

    fun onRetry() = load()

    /** Kéo xuống để làm mới: đọc lại, giữ cách sắp xếp. */
    fun onRefresh() = load(showLoading = false)

    private fun load(showLoading: Boolean = true) {
        loadJob?.cancel()
        if (showLoading) _uiState.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                all = HomeVenueSorter.sort(repository.activeVenues(), sort)
                shownCount = PAGE_SIZE
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.toAppError())
            }
        }
    }

    private fun publish() {
        _uiState.value = if (all.isEmpty()) {
            HomeUiState.Empty
        } else {
            HomeUiState.Content(
                items = all.take(shownCount),
                total = all.size,
                sort = sort,
                canLoadMore = shownCount < all.size,
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

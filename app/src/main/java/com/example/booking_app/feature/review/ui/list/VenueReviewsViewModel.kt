package com.example.booking_app.feature.review.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.feature.review.data.toReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Chip lọc trên màn "Tất cả đánh giá" (FR-013). */
sealed interface ReviewFilter {
    data object All : ReviewFilter

    /** Lọc theo `starBucket` (1–5). */
    data class Stars(val stars: Int) : ReviewFilter

    data object WithPhotos : ReviewFilter
}

data class VenueReviewsUiState(
    val venueName: String,
    val summary: VenueRatingSummary? = null,
    val filter: ReviewFilter = ReviewFilter.All,
    /** `UiState.Error.message` là tên [com.example.booking_app.feature.review.domain.ReviewError]. */
    val reviews: UiState<List<Review>> = UiState.Loading,
)

/** Màn "Tất cả đánh giá" của một sân (US6): tổng quan điểm + danh sách lọc được, mỗi trang 20. */
@HiltViewModel
class VenueReviewsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReviewRepository,
) : ViewModel() {

    private val venueId: String = checkNotNull(savedStateHandle["venueId"]) { "Thiếu venueId" }

    private val _uiState = MutableStateFlow(VenueReviewsUiState(venueName = savedStateHandle["venueName"] ?: ""))
    val uiState: StateFlow<VenueReviewsUiState> = _uiState.asStateFlow()

    private var limit = ReviewRepository.PAGE_SIZE
    private var loadedCount = 0
    private var listJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeSummary(venueId)
                .catch { /* Khối tổng quan để trống; danh sách tự báo lỗi riêng. */ }
                .collect { summary -> _uiState.update { it.copy(summary = summary) } }
        }
        observe()
    }

    fun setFilter(filter: ReviewFilter) {
        if (filter == _uiState.value.filter) return
        limit = ReviewRepository.PAGE_SIZE
        _uiState.update { it.copy(filter = filter, reviews = UiState.Loading) }
        observe()
    }

    /** Gọi khi cuộn tới cuối. Trang hiện tại chưa đầy nghĩa là đã hết đánh giá. */
    fun loadMore() {
        if (_uiState.value.reviews !is UiState.Content || loadedCount < limit) return
        limit += ReviewRepository.PAGE_SIZE
        observe()
    }

    fun retry() {
        _uiState.update { it.copy(reviews = UiState.Loading) }
        observe()
    }

    private fun observe() {
        val filter = _uiState.value.filter
        listJob?.cancel()
        listJob = viewModelScope.launch {
            repository.observeVenueReviews(
                venueId = venueId,
                stars = (filter as? ReviewFilter.Stars)?.stars,
                withPhotos = filter == ReviewFilter.WithPhotos,
                limit = limit,
            )
                .catch { e ->
                    val error = (e as? ReviewException)?.error ?: e.toReviewError()
                    _uiState.update { it.copy(reviews = UiState.Error(error.name, ::retry)) }
                }
                .collect { list ->
                    loadedCount = list.size
                    _uiState.update {
                        it.copy(reviews = if (list.isEmpty()) UiState.Empty else UiState.Content(list))
                    }
                }
        }
    }
}

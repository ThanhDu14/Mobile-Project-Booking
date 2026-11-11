package com.example.booking_app.feature.review.ui.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.feature.review.data.toReviewError
import com.example.booking_app.feature.review.domain.OwnerVenuesProvider
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.VenueOption
import com.example.booking_app.feature.review.domain.usecase.ReplyResult
import com.example.booking_app.feature.review.domain.usecase.ReplyToReviewUseCase
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerReviewsUiState(
    val venues: List<VenueOption> = emptyList(),
    /** `null` = mọi cơ sở. */
    val selectedVenueId: String? = null,
    val unrepliedOnly: Boolean = false,
    /** `UiState.Error.message` là tên [ReviewError]. */
    val reviews: UiState<List<Review>> = UiState.Loading,
    val isReplying: Boolean = false,
) {
    fun venueName(venueId: String): String? = venues.firstOrNull { it.venueId == venueId }?.name
}

sealed interface OwnerReviewsEvent {
    /** Lỗi ở client, chưa gọi server: phản hồi rỗng hoặc quá 500 ký tự. */
    data class ReplyInvalid(val empty: Boolean, val tooLong: Boolean) : OwnerReviewsEvent

    data object ReplySent : OwnerReviewsEvent

    data class ReplyFailed(val error: ReviewError) : OwnerReviewsEvent
}

/** Màn "Đánh giá của khách" của chủ sân (US4): lọc theo cơ sở, "Chưa phản hồi", gửi/sửa phản hồi. */
@HiltViewModel
class OwnerReviewsViewModel @Inject constructor(
    private val ownerVenues: OwnerVenuesProvider,
    private val repository: ReviewRepository,
    private val replyToReview: ReplyToReviewUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OwnerReviewsUiState())
    val uiState: StateFlow<OwnerReviewsUiState> = _uiState.asStateFlow()

    private val _events = Channel<OwnerReviewsEvent>(Channel.BUFFERED)
    val events: Flow<OwnerReviewsEvent> = _events.receiveAsFlow()

    private var limit = ReviewRepository.PAGE_SIZE
    private var loadedCount = 0
    private var listJob: Job? = null

    init {
        loadVenues()
    }

    fun retry() {
        if (_uiState.value.venues.isEmpty()) loadVenues() else observe()
    }

    private fun loadVenues() {
        listJob?.cancel()
        _uiState.update { it.copy(reviews = UiState.Loading) }
        viewModelScope.launch {
            try {
                val venues = ownerVenues.ownedVenues()
                _uiState.update { it.copy(venues = venues) }
                if (venues.isEmpty()) {
                    _uiState.update { it.copy(reviews = UiState.Empty) }
                } else {
                    observe()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = (e as? ReviewException)?.error ?: e.toReviewError()
                _uiState.update { it.copy(reviews = UiState.Error(error.name, ::retry)) }
            }
        }
    }

    fun selectVenue(venueId: String?) {
        if (venueId == _uiState.value.selectedVenueId) return
        _uiState.update { it.copy(selectedVenueId = venueId) }
        resetAndObserve()
    }

    fun setUnrepliedOnly(unrepliedOnly: Boolean) {
        if (unrepliedOnly == _uiState.value.unrepliedOnly) return
        _uiState.update { it.copy(unrepliedOnly = unrepliedOnly) }
        resetAndObserve()
    }

    /** Gọi khi cuộn tới cuối. */
    fun loadMore() {
        if (_uiState.value.reviews !is UiState.Content || loadedCount < limit) return
        limit += ReviewRepository.PAGE_SIZE
        observe()
    }

    fun submitReply(reviewId: String, text: String) {
        if (_uiState.value.isReplying) return
        _uiState.update { it.copy(isReplying = true) }
        viewModelScope.launch {
            val event = when (val result = replyToReview(reviewId, text)) {
                is ReplyResult.Invalid -> OwnerReviewsEvent.ReplyInvalid(result.empty, result.tooLong)
                is ReplyResult.Sent -> OwnerReviewsEvent.ReplySent
                is ReplyResult.Failed -> OwnerReviewsEvent.ReplyFailed(result.error)
            }
            _uiState.update { it.copy(isReplying = false) }
            _events.send(event)
        }
    }

    private fun resetAndObserve() {
        limit = ReviewRepository.PAGE_SIZE
        if (_uiState.value.venues.isEmpty()) return
        _uiState.update { it.copy(reviews = UiState.Loading) }
        observe()
    }

    private fun observe() {
        val state = _uiState.value
        val venueIds = state.selectedVenueId?.let(::listOf)
            ?: state.venues.map { it.venueId }.take(ReviewRepository.MAX_OWNER_VENUES)
        listJob?.cancel()
        listJob = viewModelScope.launch {
            repository.observeOwnerReviews(venueIds, state.unrepliedOnly, limit)
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

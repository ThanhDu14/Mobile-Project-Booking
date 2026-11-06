package com.example.booking_app.feature.review.ui.write

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.review.data.toReviewError
import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.model.ReviewDraft
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewResult
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Tham số Safe Args `mode` của `WriteReviewFragment`. `EDIT` dùng ở US5 (sửa đánh giá). */
enum class WriteReviewMode { CREATE, EDIT }

data class WriteReviewUiState(
    val isLoading: Boolean = true,
    val loadError: ReviewError? = null,
    val target: ReviewTarget? = null,
    val eligibility: ReviewEligibility? = null,
    val canEditUntil: Instant? = null,
    val draft: ReviewDraft,
    /** Chỉ có giá trị sau khi bấm "Gửi" mà còn thiếu (US1 kịch bản 3); chấm bổ sung thì bỏ khỏi danh sách. */
    val missingCriteria: List<ReviewCriterion> = emptyList(),
    val commentTooLong: Boolean = false,
    val isSubmitting: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
) {
    /** Điểm tổng xem trước ("— / 5" khi `null`). */
    val overall: Double? get() = SubmitReviewUseCase.overallOf(draft)

    val canSubmit: Boolean
        get() = !isLoading && loadError == null && !isSubmitting && eligibility == ReviewEligibility.CAN_WRITE

    /** Đã gửi, hết hạn hoặc đơn không hợp lệ thì chỉ xem (sửa đánh giá thuộc US5). */
    val isReadOnly: Boolean get() = eligibility != null && eligibility != ReviewEligibility.CAN_WRITE
}

sealed interface WriteReviewEvent {
    data class Submitted(val overall: Double) : WriteReviewEvent

    /** [canRetry]: snackbar có nút "Thử lại" (mất mạng, lỗi tạm thời); bản nháp được giữ nguyên. */
    data class SubmitFailed(val error: ReviewError, val canRetry: Boolean) : WriteReviewEvent
}

/** Màn hình viết đánh giá (US1). Ảnh (US2) và sửa (US5) mở rộng sau. */
@HiltViewModel
class WriteReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReviewRepository,
    private val submitReview: SubmitReviewUseCase,
    private val clock: AppClock,
) : ViewModel() {

    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"]) { "Thiếu bookingId" }

    private val _uiState = MutableStateFlow(WriteReviewUiState(draft = ReviewDraft(bookingId)))
    val uiState: StateFlow<WriteReviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<WriteReviewEvent>(Channel.BUFFERED)
    val events: Flow<WriteReviewEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun retryLoad() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val target = repository.getReviewTarget(bookingId)
                val existing = repository.observeMyReview(bookingId).first()
                val eligibility = ReviewEligibility.of(
                    target.status,
                    target.completedAt,
                    existing,
                    Instant.ofEpochMilli(clock.now()),
                )
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        target = target,
                        eligibility = eligibility,
                        canEditUntil = existing?.canEditUntil
                            ?: target.completedAt?.let(ReviewEligibility::canEditUntil),
                        draft = existing?.toDraft() ?: state.draft,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = (e as? ReviewException)?.error ?: e.toReviewError()
                _uiState.update { it.copy(isLoading = false, loadError = error) }
            }
        }
    }

    fun onRatingChanged(criterion: ReviewCriterion, stars: Int) {
        if (_uiState.value.isReadOnly || stars !in 1..5) return
        updateDraft { draft ->
            when (criterion) {
                ReviewCriterion.COURT -> draft.copy(court = stars)
                ReviewCriterion.CLEANLINESS -> draft.copy(cleanliness = stars)
                ReviewCriterion.SERVICE -> draft.copy(service = stars)
            }
        }
    }

    fun onCommentChanged(comment: String) {
        if (_uiState.value.isReadOnly || comment == _uiState.value.draft.comment) return
        updateDraft { it.copy(comment = comment) }
    }

    private fun updateDraft(change: (ReviewDraft) -> ReviewDraft) {
        _uiState.update { state ->
            val draft = change(state.draft)
            state.copy(
                draft = draft,
                hasUnsavedChanges = true,
                missingCriteria = if (state.missingCriteria.isEmpty()) {
                    emptyList()
                } else {
                    SubmitReviewUseCase.missingCriteria(draft)
                },
                commentTooLong = state.commentTooLong &&
                    draft.comment.trim().length > SubmitReviewUseCase.MAX_COMMENT,
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        // Đặt cờ trước khi launch: bấm liên tiếp chỉ tạo một lời gọi (T028).
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val result = submitReview(state.draft)) {
                is SubmitReviewResult.Invalid -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        missingCriteria = result.missing,
                        commentTooLong = result.commentTooLong,
                    )
                }
                is SubmitReviewResult.Submitted -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            hasUnsavedChanges = false,
                            eligibility = ReviewEligibility.CAN_VIEW,
                            canEditUntil = result.review.canEditUntil,
                        )
                    }
                    _events.send(WriteReviewEvent.Submitted(result.review.overall))
                }
                is SubmitReviewResult.Failed -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.send(WriteReviewEvent.SubmitFailed(result.error, result.error.isRetryable()))
                }
            }
        }
    }

    private fun ReviewError.isRetryable() = this == ReviewError.NETWORK || this == ReviewError.INTERNAL

    private fun Review.toDraft() = ReviewDraft(
        bookingId = bookingId,
        court = ratings.court,
        cleanliness = ratings.cleanliness,
        service = ratings.service,
        comment = comment,
    )
}

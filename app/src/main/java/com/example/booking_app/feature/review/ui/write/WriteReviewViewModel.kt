package com.example.booking_app.feature.review.ui.write

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.review.data.toReviewError
import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewPhotoTooLargeException
import com.example.booking_app.feature.review.domain.ReviewPhotoUploader
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.DraftPhoto
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.model.ReviewDraft
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.domain.usecase.DeleteReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewResult
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.UpdateReviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/** Tham số Safe Args `mode` của `WriteReviewFragment`: `CREATE` viết mới (US1), `EDIT` sửa (US5). */
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
    /** Đã có đánh giá và còn trong hạn 7 ngày (US5, FR-007). */
    val canEdit: Boolean = false,
    /** Đã có đánh giá; xóa được bất cứ lúc nào (FR-008). */
    val canDelete: Boolean = false,
    /** Đang sửa đánh giá đã gửi: nút "Lưu" thay cho "Gửi". */
    val isEditing: Boolean = false,
    val isDeleting: Boolean = false,
) {
    /** Điểm tổng xem trước ("— / 5" khi `null`). */
    val overall: Double? get() = SubmitReviewUseCase.overallOf(draft)

    val remainingPhotoSlots: Int get() = (ReviewPhotoUploader.MAX_PHOTOS - draft.photos.size).coerceAtLeast(0)

    /** Còn ảnh đang tải hoặc tải lỗi thì chưa gửi được (FR-006). */
    val photosReady: Boolean get() = draft.photos.all { it.state == DraftPhoto.State.UPLOADED }

    val canSubmit: Boolean
        get() = !isLoading && loadError == null && !isSubmitting && !isDeleting && photosReady &&
            (isEditing || eligibility == ReviewEligibility.CAN_WRITE)

    /** Đã gửi (chưa bấm "Sửa"), hết hạn hoặc đơn không hợp lệ thì chỉ xem. */
    val isReadOnly: Boolean
        get() = !isEditing && eligibility != null && eligibility != ReviewEligibility.CAN_WRITE
}

sealed interface WriteReviewEvent {
    data class Submitted(val overall: Double) : WriteReviewEvent

    /** Sửa đánh giá thành công (US5). */
    data class Saved(val overall: Double) : WriteReviewEvent

    /** [canRetry]: snackbar có nút "Thử lại" (mất mạng, lỗi tạm thời); bản nháp được giữ nguyên. */
    data class SubmitFailed(val error: ReviewError, val canRetry: Boolean) : WriteReviewEvent

    /** Chọn quá 3 ảnh: chỉ giữ số ảnh còn chỗ (`review_photo_limit`). */
    data object PhotoLimit : WriteReviewEvent

    /** Ảnh vẫn > 2 MB sau khi nén (`review_photo_too_large`). */
    data object PhotoTooLarge : WriteReviewEvent

    /** Hỏi lại trước khi xóa đánh giá. */
    data object ConfirmDelete : WriteReviewEvent

    data object Deleted : WriteReviewEvent

    data class DeleteFailed(val error: ReviewError) : WriteReviewEvent
}

/** Màn hình viết (US1), đính kèm ảnh (US2), sửa và xóa (US5) đánh giá của một đơn. */
@HiltViewModel
class WriteReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReviewRepository,
    private val submitReview: SubmitReviewUseCase,
    private val updateReview: UpdateReviewUseCase,
    private val deleteReview: DeleteReviewUseCase,
    private val photoUploader: ReviewPhotoUploader,
    private val clock: AppClock,
) : ViewModel() {

    /** Đúng một trong hai có giá trị: đơn đặt sân (spec 060) hoặc buổi vãng lai (spec 080 US5). */
    private val bookingId: String? = savedStateHandle.get<String>("bookingId")
    private val sessionId: String? = savedStateHandle.get<String>("sessionId")

    init {
        check(bookingId != null || sessionId != null) { "Thiếu bookingId hoặc sessionId" }
    }

    private val mode: WriteReviewMode = when (val raw = savedStateHandle.get<Any>("mode")) {
        is WriteReviewMode -> raw
        is String -> runCatching { WriteReviewMode.valueOf(raw) }.getOrDefault(WriteReviewMode.CREATE)
        else -> WriteReviewMode.CREATE
    }

    private val _uiState = MutableStateFlow(WriteReviewUiState(draft = ReviewDraft(bookingId = bookingId, sessionId = sessionId)))
    val uiState: StateFlow<WriteReviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<WriteReviewEvent>(Channel.BUFFERED)
    val events: Flow<WriteReviewEvent> = _events.receiveAsFlow()

    /** Đánh giá đã lưu trên server (sau khi nạp hoặc gửi); `null` khi chưa có. */
    private var reviewId: String? = null

    /** Path ảnh thuộc đánh giá đã lưu: bỏ ảnh này không xóa ngay, server xóa sau khi "Lưu". */
    private var savedPhotoPaths: Set<String> = emptySet()

    private val uploadJobs = mutableMapOf<String, Job>()

    init {
        load()
    }

    fun retryLoad() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val target = if (sessionId != null) {
                    repository.getDropInReviewTarget(sessionId)
                } else {
                    repository.getReviewTarget(checkNotNull(bookingId))
                }
                val existing = repository.observeMyReview(target.reviewId).first()
                val now = Instant.ofEpochMilli(clock.now())
                val eligibility = if (target.isDropIn) {
                    ReviewEligibility.ofDropIn(target.status, target.completedAt, existing, now)
                } else {
                    ReviewEligibility.of(target.status, target.completedAt, existing, now)
                }
                val canEdit = existing != null && !now.isAfter(existing.canEditUntil)
                reviewId = existing?.id
                savedPhotoPaths = existing?.photoPaths.orEmpty().toSet()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        target = target,
                        eligibility = eligibility,
                        canEditUntil = existing?.canEditUntil
                            ?: target.completedAt?.let(ReviewEligibility::canEditUntil),
                        draft = existing?.toDraft() ?: state.draft,
                        canEdit = canEdit,
                        canDelete = existing != null,
                        isEditing = canEdit && mode == WriteReviewMode.EDIT,
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

    /** Bấm "Sửa" khi đang xem đánh giá đã gửi; quá hạn thì không làm gì. */
    fun startEditing() {
        _uiState.update { if (it.canEdit && !it.isEditing) it.copy(isEditing = true) else it }
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

    // region Ảnh (US2)

    /** Ảnh vừa chọn từ Photo Picker hoặc vừa chụp. Quá chỗ trống thì chỉ giữ phần đầu và báo [WriteReviewEvent.PhotoLimit]. */
    fun addPhotos(localUris: List<String>) {
        val state = _uiState.value
        if (state.isReadOnly || state.isLoading) return
        val existing = state.draft.photos.map { it.localUri }.toSet()
        val fresh = localUris.distinct().filterNot { it in existing }
        val accepted = fresh.take(state.remainingPhotoSlots)
        if (accepted.size < fresh.size) _events.trySend(WriteReviewEvent.PhotoLimit)
        if (accepted.isEmpty()) return
        updateDraft { draft -> draft.copy(photos = draft.photos + accepted.map { DraftPhoto(it) }) }
        accepted.forEach(::startUpload)
    }

    fun retryPhoto(localUri: String) {
        val photo = _uiState.value.draft.photos.firstOrNull { it.localUri == localUri } ?: return
        if (photo.state == DraftPhoto.State.FAILED) startUpload(localUri)
    }

    /** Bỏ ảnh. Ảnh mới tải lên (chưa thuộc đánh giá đã lưu) thì xóa luôn trên Storage. */
    fun removePhoto(localUri: String) {
        val photo = _uiState.value.draft.photos.firstOrNull { it.localUri == localUri } ?: return
        if (_uiState.value.isReadOnly) return
        uploadJobs.remove(localUri)?.cancel()
        updateDraft { draft -> draft.copy(photos = draft.photos.filterNot { it.localUri == localUri }) }
        val path = photo.remotePath
        if (photo.state == DraftPhoto.State.UPLOADED && path != null && path !in savedPhotoPaths) {
            discardOnServer(listOf(path))
        }
    }

    /** Người dùng xác nhận "Bỏ đánh giá đang viết": xóa mọi ảnh đã tải lên mà chưa gắn vào đánh giá đã lưu. */
    fun discardDraft() {
        uploadJobs.values.forEach(Job::cancel)
        uploadJobs.clear()
        val paths = _uiState.value.draft.photos
            .mapNotNull { it.remotePath }
            .filterNot { it in savedPhotoPaths }
        if (paths.isNotEmpty()) discardOnServer(paths)
    }

    private fun startUpload(localUri: String) {
        setPhoto(localUri) { it.copy(state = DraftPhoto.State.UPLOADING) }
        uploadJobs[localUri]?.cancel()
        uploadJobs[localUri] = viewModelScope.launch {
            try {
                val path = photoUploader.upload(_uiState.value.target?.reviewId ?: checkNotNull(bookingId), localUri)
                setPhoto(localUri) { it.copy(remotePath = path, state = DraftPhoto.State.UPLOADED) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReviewPhotoTooLargeException) {
                setPhoto(localUri) { it.copy(state = DraftPhoto.State.FAILED) }
                _events.send(WriteReviewEvent.PhotoTooLarge)
            } catch (e: Exception) {
                setPhoto(localUri) { it.copy(state = DraftPhoto.State.FAILED) }
            } finally {
                uploadJobs.remove(localUri)
            }
        }
    }

    private fun setPhoto(localUri: String, change: (DraftPhoto) -> DraftPhoto) {
        _uiState.update { state ->
            state.copy(
                draft = state.draft.copy(
                    photos = state.draft.photos.map { if (it.localUri == localUri) change(it) else it },
                ),
            )
        }
    }

    /**
     * Xóa ảnh mồ côi trên server. Chạy [NonCancellable] để màn hình đóng ngay sau khi bấm "Bỏ" vẫn xóa
     * xong; lỗi thì bỏ qua (ảnh mồ côi không ảnh hưởng dữ liệu đánh giá).
     */
    private fun discardOnServer(paths: List<String>) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    repository.discardPhotos(bookingId, paths, sessionId)
                } catch (e: Exception) {
                    // Bỏ qua: không chặn người dùng vì ảnh mồ côi.
                }
            }
        }
    }

    // endregion

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        // Đặt cờ trước khi launch: bấm liên tiếp chỉ tạo một lời gọi (T028).
        _uiState.update { it.copy(isSubmitting = true) }
        val editingId = reviewId.takeIf { state.isEditing }
        viewModelScope.launch {
            val result = if (editingId != null) {
                updateReview(editingId, state.draft)
            } else {
                submitReview(state.draft)
            }
            when (result) {
                is SubmitReviewResult.Invalid -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        missingCriteria = result.missing,
                        commentTooLong = result.commentTooLong,
                    )
                }
                is SubmitReviewResult.Submitted -> {
                    reviewId = result.review.id
                    savedPhotoPaths = state.draft.photos.mapNotNull { it.remotePath }.toSet()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            hasUnsavedChanges = false,
                            isEditing = false,
                            eligibility = ReviewEligibility.CAN_VIEW,
                            canEditUntil = result.review.canEditUntil,
                            canEdit = !Instant.ofEpochMilli(clock.now()).isAfter(result.review.canEditUntil),
                            canDelete = true,
                        )
                    }
                    _events.send(
                        if (editingId != null) {
                            WriteReviewEvent.Saved(result.review.overall)
                        } else {
                            WriteReviewEvent.Submitted(result.review.overall)
                        },
                    )
                }
                is SubmitReviewResult.Failed -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.send(WriteReviewEvent.SubmitFailed(result.error, result.error.isRetryable()))
                }
            }
        }
    }

    // region Xóa (US5)

    fun requestDelete() {
        val state = _uiState.value
        if (state.canDelete && !state.isDeleting) _events.trySend(WriteReviewEvent.ConfirmDelete)
    }

    fun confirmDelete() {
        val id = reviewId ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val error = deleteReview(id)
            if (error == null) {
                _uiState.update { it.copy(isDeleting = false, hasUnsavedChanges = false) }
                _events.send(WriteReviewEvent.Deleted)
            } else {
                _uiState.update { it.copy(isDeleting = false) }
                _events.send(WriteReviewEvent.DeleteFailed(error))
            }
        }
    }

    // endregion

    private fun ReviewError.isRetryable() = this == ReviewError.NETWORK || this == ReviewError.INTERNAL

    private fun Review.toDraft() = ReviewDraft(
        bookingId = bookingId,
        sessionId = sessionId,
        court = ratings.court,
        cleanliness = ratings.cleanliness,
        service = ratings.service,
        comment = comment,
        photos = photoPaths.mapIndexed { i, path ->
            DraftPhoto(
                localUri = photoUrls.getOrNull(i) ?: path,
                remotePath = path,
                state = DraftPhoto.State.UPLOADED,
            )
        },
    )
}

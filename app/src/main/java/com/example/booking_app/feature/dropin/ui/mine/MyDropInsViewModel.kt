package com.example.booking_app.feature.dropin.ui.mine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.MyDropInItem
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegistrationOutcome
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.usecase.LoadMyDropIns
import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.Review
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

enum class MyDropInsTab { UPCOMING, PAST }

/**
 * Một dòng trong danh sách.
 * - [showSessionChanged]: nhãn "Buổi đã thay đổi" (AC 3.5), chỉ cho lượt còn sắp tới.
 * - [outcome]: nhãn kết quả ("Đã tham gia", "Đã hủy", "Buổi bị hủy", "Không đến"); `null` khi lượt còn sắp tới.
 * - [reviewEligibility]: nút đánh giá ở "Đã qua" (US5): "Đánh giá buổi chơi", "Xem đánh giá của bạn", "Đã hết hạn đánh giá"
 *   hoặc không hiện ([ReviewEligibility.NOT_ELIGIBLE], gồm cả lúc chưa đọc xong đánh giá của mình).
 */
data class MyDropInRow(
    val item: MyDropInItem,
    val showSessionChanged: Boolean,
    val outcome: RegistrationOutcome?,
    val reviewEligibility: ReviewEligibility = ReviewEligibility.NOT_ELIGIBLE,
)

/** Trạng thái màn hình (contracts/ui-screens.md mục 2). */
sealed interface MyDropInsUiState {
    data object Loading : MyDropInsUiState

    data class Content(
        val upcoming: List<MyDropInRow>,
        val past: List<MyDropInRow>,
        val canLoadMorePast: Boolean,
        val loadingMorePast: Boolean = false,
    ) : MyDropInsUiState

    /** Chưa có lượt nào: hiện nút "Tìm buổi vãng lai" (AC 3.6). */
    data object Empty : MyDropInsUiState

    data class Error(val cause: Throwable) : MyDropInsUiState
}

sealed interface MyDropInsEvent {
    data class OpenTicket(val sessionId: String) : MyDropInsEvent

    data object FindSessions : MyDropInsEvent

    /** Mở màn hình viết hoặc xem đánh giá của buổi (US5); chế độ xem hay viết do màn hình đánh giá tự quyết theo dữ liệu. */
    data class OpenReview(val sessionId: String) : MyDropInsEvent

    /** Tải thêm "Đã qua" lỗi: giữ danh sách đang hiện, Fragment báo bằng Snackbar. */
    data class LoadMoreFailed(val cause: Throwable) : MyDropInsEvent
}

/**
 * "Lượt vãng lai của tôi" (US3, T047). "Sắp tới" nghe realtime, giờ gần nhất trước; "Đã qua" tải theo trang 20 lượt,
 * mới nhất trước, bỏ những lượt đang nằm ở "Sắp tới" và lượt lặp giữa các trang (FR-019).
 */
@HiltViewModel
class MyDropInsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val loadMyDropIns: LoadMyDropIns,
    private val reviewRepository: ReviewRepository,
    private val clock: AppClock,
) : ViewModel() {

    /** Dữ liệu đã tải; `null` là chưa có. */
    private data class Loaded(
        val upcoming: List<MyDropInItem>? = null,
        val past: List<MyDropInItem>? = null,
        val pastCursor: DropInCursor? = null,
        val loadingMorePast: Boolean = false,
        val error: Throwable? = null,
        /** Đánh giá của mình theo `sessionId` cho các lượt đã check-in ở "Đã qua"; chưa có key là chưa đọc xong. */
        val reviews: Map<String, Review?> = emptyMap(),
    )

    private var loaded = Loaded()

    private val _uiState = MutableStateFlow<MyDropInsUiState>(MyDropInsUiState.Loading)
    val uiState: StateFlow<MyDropInsUiState> = _uiState.asStateFlow()

    val selectedTab: StateFlow<MyDropInsTab> =
        savedStateHandle.getStateFlow(KEY_TAB, MyDropInsTab.UPCOMING)

    private val _events = Channel<MyDropInsEvent>(Channel.BUFFERED)
    val events: Flow<MyDropInsEvent> = _events.receiveAsFlow()

    private var upcomingJob: Job? = null
    private var pastJob: Job? = null
    private val reviewJobs = mutableListOf<Job>()
    private val reviewRequested = mutableSetOf<String>()

    init {
        start()
    }

    fun retry() = start()

    fun selectTab(tab: MyDropInsTab) {
        savedStateHandle[KEY_TAB] = tab
    }

    fun onItemClicked(sessionId: String) {
        _events.trySend(MyDropInsEvent.OpenTicket(sessionId))
    }

    fun onReviewClicked(sessionId: String) {
        _events.trySend(MyDropInsEvent.OpenReview(sessionId))
    }

    fun onFindSessionsClicked() {
        _events.trySend(MyDropInsEvent.FindSessions)
    }

    /** Gọi khi cuộn gần cuối tab "Đã qua". */
    fun loadMorePast() {
        val current = loaded
        val cursor = current.pastCursor ?: return
        if (current.past == null || current.loadingMorePast || current.error != null || pastJob?.isActive == true) return
        update { it.copy(loadingMorePast = true) }
        pastJob = viewModelScope.launch {
            safePast(cursor)
                .onSuccess { page ->
                    update {
                        it.copy(
                            past = it.past.orEmpty() + page.items,
                            pastCursor = page.next,
                            loadingMorePast = false,
                        )
                    }
                    loadReviews()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    update { it.copy(loadingMorePast = false) }
                    _events.send(MyDropInsEvent.LoadMoreFailed(error))
                }
        }
    }

    private fun start() {
        upcomingJob?.cancel()
        pastJob?.cancel()
        reviewJobs.forEach(Job::cancel)
        reviewJobs.clear()
        reviewRequested.clear()
        loaded = Loaded()
        _uiState.value = MyDropInsUiState.Loading
        upcomingJob = viewModelScope.launch {
            loadMyDropIns.upcoming()
                .catch { error -> update { it.copy(error = error) } }
                .collect { items -> update { it.copy(upcoming = items) } }
        }
        pastJob = viewModelScope.launch {
            safePast(null)
                .onSuccess { page ->
                    update { it.copy(past = page.items, pastCursor = page.next) }
                    loadReviews()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    update { it.copy(error = error) }
                }
        }
    }

    /**
     * Đọc đánh giá của mình (`reviews/{sessionId}_{uid}`) cho các lượt đã check-in chưa đọc, để biết hiện nút "Đánh giá"
     * hay "Xem đánh giá". Đọc lỗi thì coi như chưa có đánh giá: server vẫn chặn đánh giá trùng (`ALREADY_REVIEWED`).
     */
    private fun loadReviews() {
        val pending = loaded.past.orEmpty()
            .filter { it.registration.status == RegistrationStatus.CHECKED_IN }
            .map { it.sessionId }
            .filter { reviewRequested.add(it) }
        pending.forEach { sessionId ->
            reviewJobs += viewModelScope.launch {
                val review = try {
                    reviewRepository.observeMyReview(reviewRepository.dropInReviewId(sessionId)).first()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                update { it.copy(reviews = it.reviews + (sessionId to review)) }
            }
        }
    }

    /** Usecase trả `Result`; phòng khi nó vẫn ném lỗi thì gói lại để không làm sập màn hình. */
    private suspend fun safePast(cursor: DropInCursor?): Result<Page<MyDropInItem>> =
        try {
            loadMyDropIns.past(cursor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun update(transform: (Loaded) -> Loaded) {
        loaded = transform(loaded)
        _uiState.value = loaded.toUiState()
    }

    private fun Loaded.toUiState(): MyDropInsUiState {
        error?.let { return MyDropInsUiState.Error(it) }
        val upcomingItems = upcoming ?: return MyDropInsUiState.Loading
        val pastItems = past ?: return MyDropInsUiState.Loading
        val upcomingIds = upcomingItems.mapTo(HashSet()) { it.sessionId }
        val upcomingRows = upcomingItems
            .distinctBy { it.sessionId }
            .sortedBy { it.startAt ?: Instant.MAX }
            .map { it.toRow() }
        val pastRows = pastItems
            .filterNot { it.sessionId in upcomingIds }
            .distinctBy { it.sessionId }
            .sortedByDescending { it.startAt ?: Instant.MIN }
            .map { it.toRow() }
        if (upcomingRows.isEmpty() && pastRows.isEmpty() && pastCursor == null) return MyDropInsUiState.Empty
        return MyDropInsUiState.Content(
            upcoming = upcomingRows,
            past = pastRows,
            canLoadMorePast = pastCursor != null,
            loadingMorePast = loadingMorePast,
        )
    }

    private fun MyDropInItem.toRow(): MyDropInRow {
        val stillUpcoming = outcome == RegistrationOutcome.UPCOMING
        return MyDropInRow(
            item = this,
            showSessionChanged = registration.sessionChanged && stillUpcoming,
            // Ở "Sắp tới" chỉ hiện nhãn khi có chuyện (ví dụ buổi vừa bị hủy); ở "Đã qua" luôn có nhãn kết quả.
            outcome = if (stillUpcoming) null else outcome,
            reviewEligibility = reviewEligibility(),
        )
    }

    private fun MyDropInItem.reviewEligibility(): ReviewEligibility {
        // Chưa đọc xong đánh giá của mình thì chưa hiện nút, tránh nháy "Đánh giá" rồi đổi thành "Xem đánh giá".
        if (registration.status == RegistrationStatus.CHECKED_IN && sessionId !in loaded.reviews) {
            return ReviewEligibility.NOT_ELIGIBLE
        }
        return ReviewEligibility.ofDropIn(
            registrationStatus = registration.status.name,
            sessionEndAt = endAt,
            existingReview = loaded.reviews[sessionId],
            now = Instant.ofEpochMilli(clock.now()),
        )
    }

    companion object {
        internal const val KEY_TAB = "dropin_mine_tab"
    }
}

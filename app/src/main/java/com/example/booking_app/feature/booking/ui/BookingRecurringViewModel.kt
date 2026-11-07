package com.example.booking_app.feature.booking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.booking.data.cache.VenueMetadataCache
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import com.example.booking_app.feature.booking.domain.usecase.CalculateRecurringPriceUseCase
import com.example.booking_app.feature.booking.domain.usecase.CheckRecurringAvailabilityUseCase
import com.example.booking_app.feature.booking.domain.usecase.ConflictAction
import com.example.booking_app.feature.booking.domain.usecase.ConflictResolutionResult
import com.example.booking_app.feature.booking.domain.usecase.CreateRecurringHoldTransactionUseCase
import com.example.booking_app.feature.booking.domain.usecase.GenerateRecurringSessionsUseCase
import com.example.booking_app.feature.booking.domain.usecase.ReleaseRecurringHoldUseCase
import com.example.booking_app.feature.booking.domain.usecase.ResolveRecurringConflictUseCase
import com.example.booking_app.feature.booking.ui.state.BookingRecurringUiEffect
import com.example.booking_app.feature.booking.ui.state.BookingRecurringUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BookingRecurringViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val repository: RecurringBookingRepository,
    private val generateRecurringSessionsUseCase: GenerateRecurringSessionsUseCase,
    private val checkRecurringAvailabilityUseCase: CheckRecurringAvailabilityUseCase,
    private val resolveRecurringConflictUseCase: ResolveRecurringConflictUseCase,
    private val calculateRecurringPriceUseCase: CalculateRecurringPriceUseCase,
    private val createRecurringHoldTransactionUseCase: CreateRecurringHoldTransactionUseCase,
    private val releaseRecurringHoldUseCase: ReleaseRecurringHoldUseCase,
    private val venueMetadataCache: VenueMetadataCache? = null
) : ViewModel() {

    val venueId: String = savedStateHandle["venueId"] ?: ""

    private val _uiState = MutableStateFlow(BookingRecurringUiState(venueId = venueId))
    val uiState: StateFlow<BookingRecurringUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<BookingRecurringUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    private var countdownJob: Job? = null

    // Form inputs state
    private var selectedDays: List<Int> = emptyList()
    private var selectedStartTime: String = "1800"
    private var selectedEndTime: String = "2000"
    private var selectedCourtId: String = ""
    private var selectedDurationWeeks: Int = 8
    private var isStartNextWeek: Boolean = false
    private var currentRequestId: String = UUID.randomUUID().toString()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        if (venueId.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Không tìm thấy mã cơ sở") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val isAcceptedDeferred = async { repository.isRecurringAccepted(venueId, forceRefresh) }
            val courtsDeferred = async { repository.getActiveCourts(venueId, forceRefresh) }
            val priceRulesDeferred = async { repository.getPriceRules(venueId, forceRefresh) }

            val isAcceptedResult = isAcceptedDeferred.await()
            val courtsResult = courtsDeferred.await()
            val priceRulesResult = priceRulesDeferred.await()
            val venueName = venueMetadataCache?.getVenueInfo(venueId, forceRefresh)?.name.orEmpty()

            // 1. Kiểm tra cơ sở có chấp nhận đặt cố định không (Hợp đồng Feature 111)
            val isAccepted = isAcceptedResult.getOrDefault(true)
            if (!isAccepted) {
                _uiState.update { it.copy(isLoading = false) }
                _uiEffect.send(BookingRecurringUiEffect.ShowToast("Cơ sở này hiện không nhận đặt sân cố định theo tuần"))
                _uiEffect.send(BookingRecurringUiEffect.NavigateBackToGrid)
                return@launch
            }

            // 2. Lấy danh sách sân hoạt động
            val courts = courtsResult.getOrElse { emptyList() }
            if (courts.isNotEmpty() && selectedCourtId.isEmpty()) {
                selectedCourtId = courts.first().id
            }

            // 3. Lấy bảng giá & tên cơ sở
            val priceRules = priceRulesResult.getOrElse { emptyList() }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    venueName = if (venueName.isNotEmpty()) venueName else it.venueName,
                    availableCourts = courts,
                    priceRules = priceRules
                )
            }
        }
    }

    fun onDaysSelected(days: List<Int>) {
        if (selectedDays != days) {
            selectedDays = days
            currentRequestId = UUID.randomUUID().toString()
        }
    }

    fun onTimeRangeSelected(startTime: String, endTime: String) {
        if (selectedStartTime != startTime || selectedEndTime != endTime) {
            selectedStartTime = startTime
            selectedEndTime = endTime
            currentRequestId = UUID.randomUUID().toString()
        }
    }

    fun onPreferredCourtSelected(courtId: String) {
        if (selectedCourtId != courtId) {
            selectedCourtId = courtId
            currentRequestId = UUID.randomUUID().toString()
        }
    }

    fun onDurationWeeksSelected(weeks: Int) {
        if (selectedDurationWeeks != weeks) {
            selectedDurationWeeks = weeks
            currentRequestId = UUID.randomUUID().toString()
        }
    }

    fun onStartNextWeekSelected(startNextWeek: Boolean) {
        if (isStartNextWeek != startNextWeek) {
            isStartNextWeek = startNextWeek
            currentRequestId = UUID.randomUUID().toString()
        }
    }

    fun checkAvailability() {
        if (selectedDays.isEmpty()) {
            viewModelScope.launch {
                _uiEffect.send(BookingRecurringUiEffect.ShowToast("Vui lòng chọn ít nhất một thứ trong tuần"))
            }
            return
        }

        if (selectedCourtId.isEmpty()) {
            viewModelScope.launch {
                _uiEffect.send(BookingRecurringUiEffect.ShowToast("Vui lòng chọn sân ưu tiên"))
            }
            return
        }

        val config = RecurringConfig(
            venueId = venueId,
            courtId = selectedCourtId,
            daysOfWeek = selectedDays,
            startTime = selectedStartTime,
            endTime = selectedEndTime,
            totalWeeks = selectedDurationWeeks,
            startNextWeek = isStartNextWeek
        )

        if (!config.isValidSlotRange) {
            viewModelScope.launch {
                _uiEffect.send(BookingRecurringUiEffect.ShowToast("Thời lượng buổi chơi phải từ 1 đến 3 tiếng (2 đến 6 ô 30 phút)"))
            }
            return
        }

        if (!config.isValidWeeks) {
            viewModelScope.launch {
                _uiEffect.send(BookingRecurringUiEffect.ShowToast("Chu kỳ đặt cố định phải từ 4 đến 12 tuần"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingAvailability = true, config = config, errorMessage = null) }

            val datesResult = generateRecurringSessionsUseCase(config)
            if (datesResult.isFailure) {
                val err = datesResult.exceptionOrNull()?.localizedMessage ?: "Lỗi cấu hình lịch"
                _uiState.update { it.copy(isCheckingAvailability = false, errorMessage = err) }
                _uiEffect.send(BookingRecurringUiEffect.ShowToast(err))
                return@launch
            }
            val dates = datesResult.getOrThrow()

            val checkResult = checkRecurringAvailabilityUseCase(
                venueId = venueId,
                preferredCourtId = selectedCourtId,
                dates = dates,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                allCourts = _uiState.value.availableCourts,
                priceRules = _uiState.value.priceRules
            )

            checkResult.fold(
                onSuccess = { sessions ->
                    val summary = calculateRecurringPriceUseCase.calculateSummary(sessions)
                    val hasConflicts = sessions.any {
                        it.status == RecurringSessionStatus.SUGGEST_CHANGE_COURT ||
                            it.status == RecurringSessionStatus.MUST_SKIP
                    }
                    _uiState.update {
                        it.copy(
                            isCheckingAvailability = false,
                            sessions = sessions,
                            summary = summary,
                            hasConflicts = hasConflicts
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCheckingAvailability = false,
                            errorMessage = error.localizedMessage ?: "Kiểm tra khả dụng thất bại"
                        )
                    }
                    _uiEffect.send(BookingRecurringUiEffect.ShowToast(error.localizedMessage ?: "Lỗi kiểm tra khả dụng"))
                }
            )
        }
    }

    fun onConflictActionSelected(session: RecurringSession, action: ConflictAction) {
        val currentSessions = _uiState.value.sessions
        val resolution = resolveRecurringConflictUseCase(currentSessions, session.date, action)

        when (resolution) {
            is ConflictResolutionResult.Success -> {
                val hasConflicts = resolution.updatedSessions.any {
                    it.status == RecurringSessionStatus.SUGGEST_CHANGE_COURT ||
                        it.status == RecurringSessionStatus.MUST_SKIP
                }
                _uiState.update {
                    it.copy(
                        sessions = resolution.updatedSessions,
                        summary = resolution.summary,
                        hasConflicts = hasConflicts
                    )
                }
            }
            is ConflictResolutionResult.ErrorLessThanMinSessions -> {
                viewModelScope.launch {
                    _uiEffect.send(
                        BookingRecurringUiEffect.ShowToast(
                            "Không thể bỏ thêm buổi. Gói đặt cố định yêu cầu tối thiểu 4 buổi hợp lệ."
                        )
                    )
                }
            }
        }
    }

    fun proceedToHold() {
        val currentState = _uiState.value
        val config = currentState.config ?: return

        val uid = sessionManager.currentUid()
        if (uid.isNullOrBlank()) {
            viewModelScope.launch {
                _uiEffect.send(BookingRecurringUiEffect.NavigateToLogin)
            }
            return
        }

        val validSessions = currentState.validSessions
        if (validSessions.size < 4) {
            viewModelScope.launch {
                _uiEffect.send(
                    BookingRecurringUiEffect.ShowToast("Cần tối thiểu 4 buổi hợp lệ để tiến hành giữ chỗ cố định")
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isHolding = true) }

            val requestId = currentRequestId
            val holdResult = createRecurringHoldTransactionUseCase(
                venueId = venueId,
                config = config,
                sessions = validSessions,
                userId = uid,
                requestId = requestId
            )

            holdResult.fold(
                onSuccess = { result ->
                    _uiState.update { it.copy(isHolding = false) }
                    when (result) {
                        is RecurringHoldResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    activeRecurringGroupId = result.recurringGroupId,
                                    holdCountdownSeconds = 300
                                )
                            }
                            startCountdownTimer()
                            _uiEffect.send(BookingRecurringUiEffect.NavigateToConfirm(result.recurringGroupId))
                        }
                        is RecurringHoldResult.Conflict -> {
                            _uiEffect.send(
                                BookingRecurringUiEffect.ShowErrorDialog(
                                    title = "Trùng lịch giữ chỗ",
                                    message = "Một số khung giờ trong chu kỳ vừa bị người khác giữ hoặc đặt trước. Vui lòng kiểm tra lại."
                                )
                            )
                            checkAvailability()
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isHolding = false) }
                    _uiEffect.send(
                        BookingRecurringUiEffect.ShowToast(
                            error.localizedMessage ?: "Giữ chỗ không thành công. Vui lòng thử lại."
                        )
                    )
                }
            )
        }
    }

    fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_uiState.value.holdCountdownSeconds > 0) {
                delay(1000L)
                _uiState.update { it.copy(holdCountdownSeconds = it.holdCountdownSeconds - 1) }
            }
            // Hết giờ 5 phút
            handleHoldExpired()
        }
    }

    private fun handleHoldExpired() {
        viewModelScope.launch {
            releaseHold()
            _uiEffect.send(BookingRecurringUiEffect.ShowToast("Hết thời gian giữ chỗ (5 phút)"))
            _uiEffect.send(BookingRecurringUiEffect.PopBack)
        }
    }

    fun releaseHold() {
        val groupId = _uiState.value.activeRecurringGroupId ?: return
        val slotIds = _uiState.value.validSessions.flatMap { it.slotIds }
        val uid = sessionManager.currentUid() ?: ""

        countdownJob?.cancel()
        countdownJob = null

        viewModelScope.launch {
            releaseRecurringHoldUseCase(groupId, slotIds, uid)
            _uiState.update { it.copy(activeRecurringGroupId = null) }
        }
    }

    fun continueToPayment() {
        val currentState = _uiState.value
        val groupId = currentState.activeRecurringGroupId ?: return
        val validSessions = currentState.validSessions

        val args = HoldToPaymentArgs(
            bookingId = groupId,
            venueId = venueId,
            venueName = currentState.venueName.ifEmpty { "Cơ sở cầu lông" },
            isRecurring = true,
            totalSessions = validSessions.size,
            subtotalAmount = currentState.summary?.subtotalAmount ?: 0L,
            slotIds = validSessions.flatMap { it.slotIds }.toTypedArray(),
            holdExpiresAtMillis = System.currentTimeMillis() + currentState.holdCountdownSeconds * 1000L,
            depositAmount = currentState.summary?.depositAmount ?: 0L
        )

        viewModelScope.launch {
            _uiEffect.send(BookingRecurringUiEffect.NavigateToPayment(args))
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}

package com.example.booking_app.feature.booking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotSelectionState
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import com.example.booking_app.feature.booking.domain.usecase.CalculateTotalPriceUseCase
import com.example.booking_app.feature.booking.domain.usecase.GenerateDailySlotsUseCase
import com.example.booking_app.feature.booking.domain.usecase.HoldSlotsTransactionUseCase
import com.example.booking_app.feature.booking.domain.usecase.ReleaseHoldUseCase
import com.example.booking_app.feature.booking.domain.usecase.ValidateSlotSelectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BookingSlotGridViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val bookingRepository: BookingRepository,
    private val validateSlotSelectionUseCase: ValidateSlotSelectionUseCase,
    private val calculateTotalPriceUseCase: CalculateTotalPriceUseCase,
    private val generateDailySlotsUseCase: GenerateDailySlotsUseCase,
    private val holdSlotsTransactionUseCase: HoldSlotsTransactionUseCase,
    private val releaseHoldUseCase: ReleaseHoldUseCase
) : ViewModel() {

    private val zoneVietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)

    val venueId: String = savedStateHandle["venueId"] ?: "demo-venue-01"

    private val _uiState = MutableStateFlow(
        BookingSlotUiState(
            venueId = venueId,
            dates = generate30DaysList()
        )
    )
    val uiState: StateFlow<BookingSlotUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<BookingSlotUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    private var openTime: String = "0600"
    private var closeTime: String = "2200"
    private var priceRules: List<PriceRule> = emptyList()
    private var realtimeSlotsJob: Job? = null

    // Giữ trạng thái lựa chọn hiện tại
    private var selectionState = SlotSelectionState()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Tải giờ mở/đóng cửa
            bookingRepository.getVenueOperatingHours(venueId).onSuccess { hours ->
                openTime = hours.first
                closeTime = hours.second
                val formattedHours = "${formatHourMinute(openTime)} - ${formatHourMinute(closeTime)}"
                _uiState.update { it.copy(operatingHours = formattedHours) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải thông tin cơ sở",
                        isClosed = true
                    )
                }
                return@launch
            }

            // 2. Tải bảng giá của cơ sở
            bookingRepository.getPriceRules(venueId).onSuccess { rules ->
                priceRules = rules
            }

            // 3. Tải danh sách sân con
            bookingRepository.getActiveCourts(venueId).onSuccess { courts ->
                if (courts.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            courts = emptyList(),
                            selectedCourtId = null,
                            slots = emptyList(),
                            errorMessage = "Cơ sở chưa có sân thi đấu hoạt động"
                        )
                    }
                } else {
                    val defaultCourtId = courts.first().id
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            courts = courts,
                            selectedCourtId = defaultCourtId
                        )
                    }
                    observeRealtimeSlots(defaultCourtId, _uiState.value.selectedDate)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải danh sách sân"
                    )
                }
            }
        }
    }

    fun selectDate(newDate: LocalDate) {
        if (_uiState.value.selectedDate == newDate) return

        // Xóa lựa chọn slot cũ khi đổi ngày
        clearSelection()

        _uiState.update { it.copy(selectedDate = newDate) }
        val courtId = _uiState.value.selectedCourtId ?: return
        observeRealtimeSlots(courtId, newDate)
    }

    fun selectCourt(court: Court) {
        if (_uiState.value.selectedCourtId == court.id) return

        // Xóa lựa chọn slot cũ khi đổi sân
        clearSelection()

        _uiState.update { it.copy(selectedCourtId = court.id) }
        observeRealtimeSlots(court.id, _uiState.value.selectedDate)
    }

    fun onSlotClicked(clickedSlot: TimeSlot) {
        // Phản hồi khi người dùng bấm vào các slot không trống
        when (clickedSlot.status) {
            SlotStatus.BOOKED -> {
                viewModelScope.launch {
                    _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar("Slot này đã được đặt"))
                }
                return
            }
            SlotStatus.HELD -> {
                viewModelScope.launch {
                    _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar("Slot này đang được giữ"))
                }
                return
            }
            SlotStatus.BLOCKED -> {
                viewModelScope.launch {
                    _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar("Slot này không khả dụng"))
                }
                return
            }
            SlotStatus.PAST -> {
                viewModelScope.launch {
                    _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar("Slot này đã qua giờ"))
                }
                return
            }
            SlotStatus.AVAILABLE, SlotStatus.SELECTED -> Unit
        }

        val currentSlots = _uiState.value.slots
        val newState = validateSlotSelectionUseCase(selectionState, clickedSlot, currentSlots)

        if (newState.errorMessage != null) {
            viewModelScope.launch {
                _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar(newState.errorMessage))
            }
        }

        selectionState = newState.copy(errorMessage = null)
        val selectedIds = selectionState.selectedSlots.map { it.slotId }.toSet()

        // Cập nhật lại trạng thái hiển thị của các slot trong danh sách
        val updatedSlots = currentSlots.map { slot ->
            when {
                selectedIds.contains(slot.slotId) -> slot.copy(status = SlotStatus.SELECTED)
                slot.status == SlotStatus.SELECTED -> slot.copy(status = SlotStatus.AVAILABLE)
                else -> slot
            }
        }

        _uiState.update {
            it.copy(
                slots = updatedSlots,
                selectedSlotIds = selectedIds,
                totalPrice = selectionState.totalPrice,
                depositAmount = selectionState.depositAmount,
                canProceed = selectionState.isValid
            )
        }
    }

    fun onProceedClicked() {
        // 1. Kiểm tra đăng nhập (US4)
        val userId = sessionManager.currentUid()
        if (userId == null) {
            // Lưu trạng thái lựa chọn và điều hướng đăng nhập
            viewModelScope.launch {
                _uiEffect.send(BookingSlotUiEffect.NavigateToLogin)
            }
            return
        }

        // 2. Kiểm tra điều kiện tối thiểu 2 slots (1 giờ)
        val validationResult = validateSlotSelectionUseCase.validateForProceed(selectionState)
        if (validationResult.isFailure) {
            val msg = validationResult.exceptionOrNull()?.message
                ?: "Vui lòng chọn tối thiểu 2 khung giờ liên tiếp (1 tiếng)"
            viewModelScope.launch {
                _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar(msg))
            }
            return
        }

        // 3. Thực thi giữ chỗ 5 phút nguyên tử (US2)
        val courtId = _uiState.value.selectedCourtId ?: return
        val dateString = _uiState.value.selectedDate.format(dateFormatter)
        val slotIds = selectionState.selectedSlots.map { it.slotId }
        val requestId = UUID.randomUUID().toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isHolding = true) }

            val result = holdSlotsTransactionUseCase(
                venueId = venueId,
                courtId = courtId,
                date = dateString,
                slotIds = slotIds,
                userId = userId,
                requestId = requestId
            )

            _uiState.update { it.copy(isHolding = false) }

            result.onSuccess { holdResult ->
                when (holdResult) {
                    is HoldResult.Success -> {
                        val courtName = _uiState.value.courts.find { it.id == courtId }?.name ?: "Sân"
                        val args = HoldToPaymentArgs(
                            bookingId = holdResult.bookingId,
                            venueId = venueId,
                            venueName = _uiState.value.venueName.ifEmpty { "Cơ sở $venueId" },
                            isRecurring = false,
                            totalSessions = 1,
                            subtotalAmount = selectionState.totalPrice,
                            slotIds = slotIds.toTypedArray(),
                            holdExpiresAtMillis = holdResult.holdExpiresAtMillis
                        )
                        _uiEffect.send(BookingSlotUiEffect.NavigateToBookingSummary(args))
                    }

                    is HoldResult.Conflict -> {
                        _uiEffect.send(
                            BookingSlotUiEffect.ShowConflictWarning(
                                "Một số khung giờ bạn chọn vừa có người đặt hoặc giữ chỗ. Vui lòng chọn lại."
                            )
                        )
                        clearSelection()
                    }
                }
            }.onFailure { error ->
                _uiEffect.send(
                    BookingSlotUiEffect.ShowErrorSnackbar(
                        error.message ?: "Có lỗi xảy ra khi giữ chỗ, vui lòng thử lại"
                    )
                )
            }
        }
    }

    fun hasSelectedSlots(): Boolean = _uiState.value.selectedSlotIds.isNotEmpty()

    fun clearSelection() {
        selectionState = SlotSelectionState()
        val clearedSlots = _uiState.value.slots.map {
            if (it.status == SlotStatus.SELECTED) it.copy(status = SlotStatus.AVAILABLE) else it
        }
        _uiState.update {
            it.copy(
                slots = clearedSlots,
                selectedSlotIds = emptySet(),
                totalPrice = 0L,
                depositAmount = 0L,
                canProceed = false
            )
        }
    }

    private fun observeRealtimeSlots(courtId: String, date: LocalDate) {
        realtimeSlotsJob?.cancel()
        val dateString = date.format(dateFormatter)

        realtimeSlotsJob = viewModelScope.launch {
            bookingRepository.getBusySlotsRealtime(venueId, dateString)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
                .collect { busySlots ->
                    // Sinh toàn bộ ca 30 phút theo giờ mở/đóng cửa và gộp slot bận
                    val allSlots = generateDailySlotsUseCase(
                        courtId = courtId,
                        date = dateString,
                        openTime = openTime,
                        closeTime = closeTime,
                        priceRules = priceRules,
                        busySlots = busySlots,
                        selectedSlotIds = selectionState.selectedSlots.map { it.slotId }.toSet()
                    )

                    // Kiểm tra xung đột Realtime: nếu slot đang chọn bị người khác giữ hoặc đặt mất
                    val currentlySelectedIds = selectionState.selectedSlots.map { it.slotId }
                    val conflictedSlots = allSlots.filter { slot ->
                        currentlySelectedIds.contains(slot.slotId) &&
                                (slot.status == SlotStatus.HELD || slot.status == SlotStatus.BOOKED || slot.status == SlotStatus.BLOCKED)
                    }

                    if (conflictedSlots.isNotEmpty()) {
                        // Loại bỏ các slot bị xung đột khỏi vùng chọn
                        val remainingSlots = selectionState.selectedSlots.filter { selected ->
                            conflictedSlots.none { it.slotId == selected.slotId }
                        }
                        val (newTotal, newDeposit) = calculateTotalPriceUseCase(remainingSlots)
                        selectionState = SlotSelectionState(
                            selectedSlots = remainingSlots,
                            totalPrice = newTotal,
                            depositAmount = newDeposit,
                            isValid = remainingSlots.size >= ValidateSlotSelectionUseCase.MIN_SLOTS
                        )
                        _uiEffect.send(
                            BookingSlotUiEffect.ShowConflictWarning(
                                "Khung giờ bạn chọn vừa có người đặt hoặc giữ chỗ. Vui lòng chọn lại."
                            )
                        )
                    }

                    // Cập nhật lại UI state với danh sách slot mới
                    val selectedIds = selectionState.selectedSlots.map { it.slotId }.toSet()
                    val displaySlots = allSlots.map { slot ->
                        if (selectedIds.contains(slot.slotId)) {
                            slot.copy(status = SlotStatus.SELECTED)
                        } else {
                            slot
                        }
                    }

                    _uiState.update {
                        it.copy(
                            slots = displaySlots,
                            selectedSlotIds = selectedIds,
                            totalPrice = selectionState.totalPrice,
                            depositAmount = selectionState.depositAmount,
                            canProceed = selectionState.isValid
                        )
                    }
                }
        }
    }

    private fun generate30DaysList(): List<LocalDate> {
        val today = LocalDate.now(zoneVietnam)
        return (0L until 30L).map { today.plusDays(it) }
    }

    private fun formatHourMinute(hhmm: String): String {
        if (hhmm.length < 4) return hhmm
        return "${hhmm.substring(0, 2)}:${hhmm.substring(2, 4)}"
    }
}

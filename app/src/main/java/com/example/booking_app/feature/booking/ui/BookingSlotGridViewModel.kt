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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DecimalFormat
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
    private val currencyFormatter = DecimalFormat("#,###", java.text.DecimalFormatSymbols(Locale.GERMANY))

    // Q12: Bỏ fallback "demo-venue-01" im lặng; venueId bắt buộc
    val venueId: String = savedStateHandle["venueId"] ?: ""

    private val _uiState = MutableStateFlow(
        BookingSlotUiState(
            venueId = venueId,
            dates = generate30DaysList()
        )
    )
    val uiState: StateFlow<BookingSlotUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<BookingSlotUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    private var openTime: String = "0500"
    private var closeTime: String = "2400"
    private var priceRules: List<PriceRule> = emptyList()
    private var activeCourts: List<Court> = emptyList()
    private var realtimeSlotsJob: Job? = null
    private var periodicExpiryJob: Job? = null
    private var latestBusySlots: List<TimeSlot> = emptyList()
    private var currentRequestId: String = UUID.randomUUID().toString()

    // Giữ trạng thái lựa chọn hiện tại (hỗ trợ đa sân theo Q1)
    private var selectionState = SlotSelectionState()

    init {
        if (venueId.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Không tìm thấy mã cơ sở đặt sân (venueId không hợp lệ)",
                    isClosed = true
                )
            }
        } else {
            loadInitialData()
        }
    }

    fun loadInitialData() {
        loadVenueAndSlots()
    }

    fun loadVenueAndSlots(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val venueDeferred = async { bookingRepository.getVenueInfo(venueId, forceRefresh) }
            val priceRulesDeferred = async { bookingRepository.getPriceRules(venueId, forceRefresh) }
            val courtsDeferred = async { bookingRepository.getActiveCourts(venueId, forceRefresh) }

            val venueResult = venueDeferred.await()
            val priceRulesResult = priceRulesDeferred.await()
            val courtsResult = courtsDeferred.await()

            // 1. Tải thông tin chi tiết cơ sở (Q12: getVenueInfo tên + địa chỉ + quận)
            var hasVenueError = false
            venueResult.onSuccess { info ->
                if (info.status != null && info.status != "ACTIVE") {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Cơ sở hiện không hoạt động (trạng thái: ${info.status})",
                            isClosed = true
                        )
                    }
                    hasVenueError = true
                    return@onSuccess
                }
                openTime = info.openTime
                closeTime = info.closeTime
                val formattedHours = "${formatHourMinute(openTime)} - ${formatHourMinute(closeTime)}"
                _uiState.update {
                    it.copy(
                        venueName = info.name,
                        venueAddress = info.address,
                        districtName = info.districtName,
                        photoUrl = info.photoUrl,
                        operatingHours = formattedHours
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải thông tin cơ sở",
                        isClosed = true
                    )
                }
                hasVenueError = true
            }
            if (hasVenueError) return@launch

            // 2. Tải bảng giá của cơ sở (Q5: dynamic peak hour banner từ dữ liệu thật)
            var hasPriceRulesError = false
            priceRulesResult.onSuccess { rules ->
                if (rules.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Cơ sở chưa có bảng giá hoạt động",
                            isClosed = true
                        )
                    }
                    hasPriceRulesError = true
                    return@onSuccess
                }
                priceRules = rules
                val peakBanner = computePeakHourBanner(rules)
                _uiState.update { it.copy(peakHourBannerText = peakBanner) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải bảng giá",
                        isClosed = true
                    )
                }
                hasPriceRulesError = true
            }
            if (hasPriceRulesError) return@launch

            // 3. Tải danh sách sân con đang hoạt động (Q3: ma trận mọi sân)
            courtsResult.onSuccess { courts ->
                activeCourts = courts
                if (courts.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            courts = emptyList(),
                            matrixRows = emptyList(),
                            errorMessage = "Cơ sở chưa có sân thi đấu hoạt động"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            courts = courts
                        )
                    }
                    observeRealtimeSlots(_uiState.value.selectedDate)
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

        // Xóa lựa chọn slot khi đổi ngày (US1-AS8)
        clearSelection()

        _uiState.update { it.copy(selectedDate = newDate) }
        observeRealtimeSlots(newDate)
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
                val now = System.currentTimeMillis()
                val expiresAt = clickedSlot.holdExpiresAtMillis
                if (expiresAt != null && expiresAt <= now) {
                    // Hết hạn HELD: cho phép chọn bình thường (P0-4)
                } else {
                    viewModelScope.launch {
                        _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar("Slot này đang được giữ"))
                    }
                    return
                }
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

        // Kiểm tra cap 8 khung tính theo TỪNG SÂN (FR-026)
        val targetCourtSlots = selectionState.selectedSlots.filter { it.courtId == clickedSlot.courtId }
        val isAlreadySelected = selectionState.selectedSlots.any { it.slotId == clickedSlot.slotId }
        val courtName = activeCourts.find { it.id == clickedSlot.courtId }?.name ?: "Sân"

        if (!isAlreadySelected && targetCourtSlots.size >= ValidateSlotSelectionUseCase.MAX_SLOTS_PER_COURT) {
            viewModelScope.launch {
                _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar("$courtName đã đủ ${ValidateSlotSelectionUseCase.MAX_SLOTS_PER_COURT} khung (4 giờ)"))
            }
            return
        }

        val allCurrentSlots = _uiState.value.matrixRows.flatMap { it.slots }
        val newState = validateSlotSelectionUseCase(selectionState, clickedSlot, allCurrentSlots)

        if (newState.errorMessage != null) {
            viewModelScope.launch {
                _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar(newState.errorMessage))
            }
        }

        val oldSelectedSlots = selectionState.selectedSlots
        selectionState = newState.copy(errorMessage = null)
        if (oldSelectedSlots != selectionState.selectedSlots) {
            currentRequestId = UUID.randomUUID().toString()
        }
        val selectedIds = selectionState.selectedSlots.map { it.slotId }.toSet()

        // Cập nhật lại ma trận hàng/cột
        val updatedMatrixRows = _uiState.value.matrixRows.map { row ->
            val updatedSlots = row.slots.map { slot ->
                when {
                    selectedIds.contains(slot.slotId) -> slot.copy(status = SlotStatus.SELECTED)
                    slot.status == SlotStatus.SELECTED -> slot.copy(status = SlotStatus.AVAILABLE)
                    else -> slot
                }
            }
            row.copy(slots = updatedSlots)
        }

        val courtNameMap = activeCourts.associate { it.id to it.name }
        val incompleteHint = validateSlotSelectionUseCase.findIncompleteCourt(selectionState.selectedSlots, courtNameMap)
        val summaryText = incompleteHint ?: formatSelectionSummary(selectionState.selectedSlots)

        _uiState.update {
            it.copy(
                matrixRows = updatedMatrixRows,
                selectedSlots = selectionState.selectedSlots,
                selectedSlotIds = selectedIds,
                totalPrice = selectionState.totalPrice,
                depositAmount = selectionState.depositAmount,
                canProceed = selectionState.isValid,
                selectionDetailSummary = summaryText
            )
        }
    }

    fun onProceedClicked() {
        // 1. Kiểm tra đăng nhập (Q10, US4)
        val userId = sessionManager.currentUid()
        if (userId == null) {
            // Lưu trạng thái lựa chọn vào SavedStateHandle để khôi phục sau khi đăng nhập
            savedStateHandle["pending_selected_slot_ids"] = selectionState.selectedSlots.map { it.slotId }
            savedStateHandle["pending_selected_date"] = _uiState.value.selectedDate.toString()
            viewModelScope.launch {
                _uiEffect.send(BookingSlotUiEffect.NavigateToLogin)
            }
            return
        }

        // 2. Kiểm tra khoảng trống lẻ 1 khung (bám sát luật §5)
        val allCurrentSlots = _uiState.value.matrixRows.flatMap { it.slots }
        val courtGroups = selectionState.selectedSlots.groupBy { it.courtId }
        val courtNameMap = activeCourts.associate { it.id to it.name }

        for ((courtId, courtSlots) in courtGroups) {
            val invalidGaps = validateSlotSelectionUseCase.findSingleSlotGaps(courtSlots)
            if (invalidGaps.isNotEmpty()) {
                val gap = invalidGaps.first()
                val courtName = courtNameMap[courtId] ?: "Sân"

                // Tìm ô trống lẻ trong ma trận
                val gapSlot = allCurrentSlots.find {
                    it.courtId == courtId && it.startTime == gap.gapStartTime && it.endTime == gap.gapEndTime
                }
                val canAdd = gapSlot != null &&
                    gapSlot.status == SlotStatus.AVAILABLE &&
                    courtSlots.size + 1 <= ValidateSlotSelectionUseCase.MAX_SLOTS_PER_COURT

                // Khối phía sau khoảng trống lẻ
                val trailingSlots = courtSlots.filter { it.startTime >= gap.gapEndTime }.sortedBy { it.startTime }
                val trailingRange = if (trailingSlots.isNotEmpty()) {
                    val start = formatHourMinute(trailingSlots.first().startTime)
                    val end = formatHourMinute(trailingSlots.last().endTime)
                    "$start–$end"
                } else {
                    ""
                }

                viewModelScope.launch {
                    _uiEffect.send(
                        BookingSlotUiEffect.ShowGapResolutionDialog(
                            courtName = courtName,
                            gapInfo = gap,
                            canAddSlot = canAdd,
                            gapSlotToAdd = gapSlot,
                            trailingBlockSlotsToRemove = trailingSlots,
                            trailingBlockRange = trailingRange
                        )
                    )
                }
                return
            }
        }

        // 3. Kiểm tra điều kiện tối thiểu 2 slots và tối đa 8 slots theo từng sân (FR-026)
        val validationResult = validateSlotSelectionUseCase.validateForProceed(selectionState, courtNameMap)
        if (validationResult.isFailure) {
            val msg = validationResult.exceptionOrNull()?.message
                ?: "Vui lòng chọn tối thiểu 2 khung giờ cho mỗi sân"
            viewModelScope.launch {
                _uiEffect.send(BookingSlotUiEffect.ShowErrorSnackbar(msg))
            }
            return
        }

        // 3. Thực thi giữ chỗ 5 phút nguyên tử đa sân trong 1 transaction (Q1, Q4, Q8)
        val dateString = _uiState.value.selectedDate.format(dateFormatter)
        val slotIds = selectionState.selectedSlots.map { it.slotId }
        val requestId = currentRequestId

        viewModelScope.launch {
            _uiState.update { it.copy(isHolding = true) }

            val result = holdSlotsTransactionUseCase(
                venueId = venueId,
                courtId = "", // FirestoreBookingRemoteDataSource tự parse itemCourtId từ slotId
                date = dateString,
                slotIds = slotIds,
                userId = userId,
                requestId = requestId
            )

            _uiState.update { it.copy(isHolding = false) }

            result.onSuccess { holdResult ->
                when (holdResult) {
                    is HoldResult.Success -> {
                        val courtDetails = buildCourtDetailsForConfirm(selectionState.selectedSlots)
                        val args = HoldToPaymentArgs(
                            bookingId = holdResult.bookingId,
                            venueId = venueId,
                            venueName = _uiState.value.venueName.ifEmpty { "Cơ sở $venueId" },
                            venueAddress = _uiState.value.venueAddress,
                            districtName = _uiState.value.districtName,
                            photoUrl = _uiState.value.photoUrl.orEmpty(),
                            isRecurring = false,
                            totalSessions = 1,
                            subtotalAmount = selectionState.totalPrice,
                            slotIds = slotIds.toTypedArray(),
                            holdExpiresAtMillis = holdResult.holdExpiresAtMillis,
                            courtDetails = courtDetails.toTypedArray()
                        )
                        _uiEffect.send(BookingSlotUiEffect.NavigateToBookingSummary(args))
                    }

                    is HoldResult.Conflict -> {
                        // Q8: Chỉ loại bỏ slot mất, nêu tên slot mất, giữ nguyên slot còn trống
                        handleHoldConflict(holdResult.conflictedSlotIds)
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

    private fun handleHoldConflict(conflictedSlotIds: List<String>) {
        val lostSlotNames = selectionState.selectedSlots
            .filter { conflictedSlotIds.contains(it.slotId) }
            .map { formatSlotShortName(it) }

        val remainingSlots = selectionState.selectedSlots.filter {
            !conflictedSlotIds.contains(it.slotId)
        }

        val (newTotal, newDeposit) = calculateTotalPriceUseCase(remainingSlots)
        val isValid = validateSlotSelectionUseCase.isSelectionValid(remainingSlots)
        selectionState = SlotSelectionState(
            selectedSlots = remainingSlots,
            totalPrice = newTotal,
            depositAmount = newDeposit,
            isValid = isValid
        )

        val selectedIds = remainingSlots.map { it.slotId }.toSet()
        val updatedMatrixRows = _uiState.value.matrixRows.map { row ->
            val updatedSlots = row.slots.map { slot ->
                when {
                    selectedIds.contains(slot.slotId) -> slot.copy(status = SlotStatus.SELECTED)
                    slot.status == SlotStatus.SELECTED -> slot.copy(status = SlotStatus.AVAILABLE)
                    else -> slot
                }
            }
            row.copy(slots = updatedSlots)
        }

        val courtNameMap = activeCourts.associate { it.id to it.name }
        val incompleteHint = validateSlotSelectionUseCase.findIncompleteCourt(remainingSlots, courtNameMap)
        val summaryText = incompleteHint ?: formatSelectionSummary(remainingSlots)

        _uiState.update {
            it.copy(
                matrixRows = updatedMatrixRows,
                selectedSlots = remainingSlots,
                selectedSlotIds = selectedIds,
                totalPrice = newTotal,
                depositAmount = newDeposit,
                canProceed = selectionState.isValid,
                selectionDetailSummary = summaryText
            )
        }

        val message = if (lostSlotNames.isNotEmpty()) {
            "Khung giờ ${lostSlotNames.joinToString(", ")} vừa có người đặt hoặc giữ chỗ. Đã giữ lại các khung giờ còn trống."
        } else {
            "Một số khung giờ bạn chọn vừa có người đặt hoặc giữ chỗ. Đã cập nhật lại."
        }

        viewModelScope.launch {
            _uiEffect.send(BookingSlotUiEffect.ShowConflictWarning(message))
        }
    }

    fun onAddGapSlot(slot: TimeSlot) {
        onSlotClicked(slot)
    }

    fun onRemoveTrailingBlock(slotsToRemove: List<TimeSlot>) {
        val remainingSlots = selectionState.selectedSlots.filterNot { selected ->
            slotsToRemove.any { it.slotId == selected.slotId }
        }
        applyUpdatedSlots(remainingSlots)
    }

    private fun applyUpdatedSlots(slots: List<TimeSlot>) {
        val selectedIds = slots.map { it.slotId }.toSet()
        val (total, deposit) = calculateTotalPriceUseCase(slots)
        selectionState = selectionState.copy(
            selectedSlots = slots,
            totalPrice = total,
            depositAmount = deposit,
            isValid = validateSlotSelectionUseCase.isSelectionValid(slots)
        )

        val updatedMatrixRows = _uiState.value.matrixRows.map { row ->
            val updatedSlots = row.slots.map { slot ->
                when {
                    selectedIds.contains(slot.slotId) -> slot.copy(status = SlotStatus.SELECTED)
                    slot.status == SlotStatus.SELECTED -> slot.copy(status = SlotStatus.AVAILABLE)
                    else -> slot
                }
            }
            row.copy(slots = updatedSlots)
        }

        val courtNameMap = activeCourts.associate { it.id to it.name }
        val incompleteHint = validateSlotSelectionUseCase.findIncompleteCourt(slots, courtNameMap)
        val summaryText = incompleteHint ?: formatSelectionSummary(slots)

        _uiState.update {
            it.copy(
                matrixRows = updatedMatrixRows,
                selectedSlots = slots,
                selectedSlotIds = selectedIds,
                totalPrice = total,
                depositAmount = deposit,
                canProceed = selectionState.isValid,
                selectionDetailSummary = summaryText
            )
        }
    }

    fun checkAndRestorePendingSelection() {
        val pendingSlotIds = savedStateHandle.get<List<String>>("pending_selected_slot_ids") ?: return
        savedStateHandle.remove<List<String>>("pending_selected_slot_ids")
        if (pendingSlotIds.isEmpty()) return

        val allSlots = _uiState.value.matrixRows.flatMap { it.slots }
        val availablePendingSlots = allSlots.filter { slot ->
            pendingSlotIds.contains(slot.slotId) && slot.status == SlotStatus.AVAILABLE
        }
        val lostSlotIds = pendingSlotIds.filter { id -> availablePendingSlots.none { it.slotId == id } }

        if (availablePendingSlots.isNotEmpty()) {
            val (newTotal, newDeposit) = calculateTotalPriceUseCase(availablePendingSlots)
            val isValid = validateSlotSelectionUseCase.isSelectionValid(availablePendingSlots)
            selectionState = SlotSelectionState(
                selectedSlots = availablePendingSlots,
                totalPrice = newTotal,
                depositAmount = newDeposit,
                isValid = isValid
            )
            val selectedIds = availablePendingSlots.map { it.slotId }.toSet()
            val updatedMatrixRows = _uiState.value.matrixRows.map { row ->
                val updatedSlots = row.slots.map { slot ->
                    if (selectedIds.contains(slot.slotId)) slot.copy(status = SlotStatus.SELECTED) else slot
                }
                row.copy(slots = updatedSlots)
            }
            val courtNameMap = activeCourts.associate { it.id to it.name }
            val incompleteHint = validateSlotSelectionUseCase.findIncompleteCourt(availablePendingSlots, courtNameMap)
            val summaryText = incompleteHint ?: formatSelectionSummary(availablePendingSlots)

            _uiState.update {
                it.copy(
                    matrixRows = updatedMatrixRows,
                    selectedSlots = availablePendingSlots,
                    selectedSlotIds = selectedIds,
                    totalPrice = newTotal,
                    depositAmount = newDeposit,
                    canProceed = selectionState.isValid,
                    selectionDetailSummary = summaryText
                )
            }
        }

        if (lostSlotIds.isNotEmpty()) {
            viewModelScope.launch {
                _uiEffect.send(
                    BookingSlotUiEffect.ShowConflictWarning(
                        "Một số khung giờ bạn chọn trước khi đăng nhập hiện đã không còn trống."
                    )
                )
            }
        }
    }

    fun hasSelectedSlots(): Boolean = _uiState.value.selectedSlotIds.isNotEmpty()

    fun clearSelection() {
        currentRequestId = UUID.randomUUID().toString()
        selectionState = SlotSelectionState()
        val updatedMatrixRows = _uiState.value.matrixRows.map { row ->
            val updatedSlots = row.slots.map { slot ->
                if (slot.status == SlotStatus.SELECTED) slot.copy(status = SlotStatus.AVAILABLE) else slot
            }
            row.copy(slots = updatedSlots)
        }
        _uiState.update {
            it.copy(
                matrixRows = updatedMatrixRows,
                selectedSlots = emptyList(),
                selectedSlotIds = emptySet(),
                totalPrice = 0L,
                depositAmount = 0L,
                canProceed = false,
                selectionDetailSummary = ""
            )
        }
    }

    private fun observeRealtimeSlots(date: LocalDate) {
        realtimeSlotsJob?.cancel()
        val dateString = date.format(dateFormatter)

        realtimeSlotsJob = viewModelScope.launch {
            bookingRepository.getBusySlotsRealtime(venueId, dateString)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
                .collect { busySlots ->
                    latestBusySlots = busySlots
                    processBusySlots(busySlots, dateString)
                }
        }
    }

    fun startPeriodicExpiryCheck() {
        periodicExpiryJob?.cancel()
        periodicExpiryJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                if (latestBusySlots.isNotEmpty() && activeCourts.isNotEmpty()) {
                    val dateString = _uiState.value.selectedDate.format(dateFormatter)
                    processBusySlots(latestBusySlots, dateString)
                }
            }
        }
    }

    fun stopPeriodicExpiryCheck() {
        periodicExpiryJob?.cancel()
        periodicExpiryJob = null
    }

    private suspend fun processBusySlots(busySlots: List<TimeSlot>, dateString: String) {
        if (activeCourts.isEmpty()) return

        val selectedIds = selectionState.selectedSlots.map { it.slotId }.toSet()

        // Sinh slot cho từng sân con (P0-4: giải phóng HELD đã hết hạn với currentTimeMillis)
        val courtSlotsMap = activeCourts.associate { court ->
            court.id to generateDailySlotsUseCase(
                courtId = court.id,
                date = dateString,
                openTime = openTime,
                closeTime = closeTime,
                priceRules = priceRules,
                busySlots = busySlots,
                selectedSlotIds = selectedIds,
                currentTimeMillis = System.currentTimeMillis()
            )
        }

        // Xây dựng các hàng ma trận (SlotMatrixRow)
        // Lấy tất cả các mốc thời gian duy nhất từ các sân (thường giống nhau)
        val sampleCourtSlots = courtSlotsMap[activeCourts.first().id] ?: emptyList()
        val matrixRows = sampleCourtSlots.map { sampleSlot ->
            val timeRaw = sampleSlot.startTime
            val timeFormatted = formatHourMinute(timeRaw)
            val rowSlots = activeCourts.mapNotNull { court ->
                courtSlotsMap[court.id]?.find { it.startTime == timeRaw }
            }
            SlotMatrixRow(
                timeFormatted = timeFormatted,
                timeRaw = timeRaw,
                slots = rowSlots
            )
        }

        // Kiểm tra xung đột Realtime: nếu slot đang chọn bị người khác giữ/đặt mất
        val allSlotsFlat = matrixRows.flatMap { it.slots }
        val conflictedSlots = allSlotsFlat.filter { slot ->
            selectedIds.contains(slot.slotId) &&
                    (slot.status == SlotStatus.HELD || slot.status == SlotStatus.BOOKED || slot.status == SlotStatus.BLOCKED)
        }

        if (conflictedSlots.isNotEmpty()) {
            val remainingSlots = selectionState.selectedSlots.filter { selected ->
                conflictedSlots.none { it.slotId == selected.slotId }
            }
            val (newTotal, newDeposit) = calculateTotalPriceUseCase(remainingSlots)
            val isValid = validateSlotSelectionUseCase.isSelectionValid(remainingSlots)
            selectionState = SlotSelectionState(
                selectedSlots = remainingSlots,
                totalPrice = newTotal,
                depositAmount = newDeposit,
                isValid = isValid
            )
            _uiEffect.send(
                BookingSlotUiEffect.ShowConflictWarning(
                    "Khung giờ bạn chọn vừa có người đặt hoặc giữ chỗ. Vui lòng chọn lại."
                )
            )
        }

        // Đánh dấu lại trạng thái SELECTED cho matrixRows
        val currentSelectedIds = selectionState.selectedSlots.map { it.slotId }.toSet()
        val displayMatrixRows = matrixRows.map { row ->
            val updatedSlots = row.slots.map { slot ->
                if (currentSelectedIds.contains(slot.slotId)) {
                    slot.copy(status = SlotStatus.SELECTED)
                } else {
                    slot
                }
            }
            row.copy(slots = updatedSlots)
        }

        // Kiểm tra kín sân toàn bộ ngày
        val isFullyBooked = allSlotsFlat.isNotEmpty() && allSlotsFlat.all {
            it.status == SlotStatus.BOOKED || it.status == SlotStatus.HELD || it.status == SlotStatus.BLOCKED || it.status == SlotStatus.PAST
        }

        val courtNameMap = activeCourts.associate { it.id to it.name }
        val incompleteHint = validateSlotSelectionUseCase.findIncompleteCourt(selectionState.selectedSlots, courtNameMap)
        val summaryText = incompleteHint ?: formatSelectionSummary(selectionState.selectedSlots)

        _uiState.update {
            it.copy(
                matrixRows = displayMatrixRows,
                slots = allSlotsFlat,
                selectedSlots = selectionState.selectedSlots,
                selectedSlotIds = currentSelectedIds,
                totalPrice = selectionState.totalPrice,
                depositAmount = selectionState.depositAmount,
                canProceed = selectionState.isValid,
                isFullyBooked = isFullyBooked,
                selectionDetailSummary = summaryText
            )
        }
    }

    private fun computePeakHourBanner(rules: List<PriceRule>): String? {
        if (rules.isEmpty()) return null
        val peakRule = rules.maxByOrNull { it.pricePerHour } ?: return null
        val startFormatted = formatHourMinute(peakRule.startTime)
        val endFormatted = formatHourMinute(peakRule.endTime)
        val priceFormatted = currencyFormatter.format(peakRule.pricePerHour)
        return "Giờ cao điểm $startFormatted–$endFormatted · <b>$priceFormatted đ/giờ</b>"
    }

    private fun formatSelectionSummary(slots: List<TimeSlot>): String {
        if (slots.isEmpty()) return ""
        val courtGroups = slots.groupBy { it.courtId }
        val parts = mutableListOf<String>()

        for ((courtId, courtSlots) in courtGroups) {
            val courtName = activeCourts.find { it.id == courtId }?.name ?: "Sân"
            val times = courtSlots.map { it.startTime }.sorted()
            if (times.isNotEmpty()) {
                val start = formatHourMinute(times.first())
                val end = add30Minutes(times.last())
                val hours = courtSlots.size * 0.5
                val hoursText = if (hours % 1.0 == 0.0) "${hours.toInt()} giờ" else "$hours giờ"
                val date = courtSlots.first().date
                val dateText = formatDateForSummary(date)
                parts.add("$courtName · $dateText · $start – $end · $hoursText")
            }
        }
        return parts.joinToString("\n")
    }

    private fun buildCourtDetailsForConfirm(slots: List<TimeSlot>): List<String> {
        val courtGroups = slots.groupBy { it.courtId }
        val result = mutableListOf<String>()
        var courtIndex = 1

        for ((courtId, courtSlots) in courtGroups) {
            val court = activeCourts.find { it.id == courtId }
            val courtBadge = "S$courtIndex"
            val courtName = court?.name ?: "Sân $courtIndex"
            val surfaceType = court?.surfaceType.orEmpty()
            val times = courtSlots.map { it.startTime }.sorted()
            val start = if (times.isNotEmpty()) formatHourMinute(times.first()) else ""
            val end = if (times.isNotEmpty()) add30Minutes(times.last()) else ""
            val dateText = if (courtSlots.isNotEmpty()) formatDateForSummary(courtSlots.first().date) else ""
            val timeRangeText = "$start – $end"
            val subtotal = courtSlots.sumOf { it.price }

            result.add("$courtBadge|$courtName|$surfaceType|$dateText|$timeRangeText|$subtotal")
            courtIndex++
        }
        return result
    }

    private fun formatDateForSummary(dateStr: String): String {
        return try {
            val date = if (dateStr.length == 8 && dateStr.all { it.isDigit() }) {
                val y = dateStr.substring(0, 4).toInt()
                val m = dateStr.substring(4, 6).toInt()
                val d = dateStr.substring(6, 8).toInt()
                LocalDate.of(y, m, d)
            } else {
                LocalDate.parse(dateStr)
            }
            val dow = when (date.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "T2"
                java.time.DayOfWeek.TUESDAY -> "T3"
                java.time.DayOfWeek.WEDNESDAY -> "T4"
                java.time.DayOfWeek.THURSDAY -> "T5"
                java.time.DayOfWeek.FRIDAY -> "T6"
                java.time.DayOfWeek.SATURDAY -> "T7"
                java.time.DayOfWeek.SUNDAY -> "CN"
                else -> ""
            }
            val dayMonth = String.format(Locale.US, "%02d/%02d", date.dayOfMonth, date.monthValue)
            "$dow, $dayMonth"
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun formatSlotShortName(slot: TimeSlot): String {
        val courtName = activeCourts.find { it.id == slot.courtId }?.name ?: "Sân"
        val time = formatHourMinute(slot.startTime)
        return "$time ($courtName)"
    }

    private fun generate30DaysList(): List<LocalDate> {
        val today = LocalDate.now(zoneVietnam)
        return (0L until 30L).map { today.plusDays(it) }
    }

    private fun formatHourMinute(hhmm: String): String {
        if (hhmm.length < 4) return hhmm
        return "${hhmm.substring(0, 2)}:${hhmm.substring(2, 4)}"
    }

    private fun add30Minutes(hhmm: String): String {
        if (hhmm.length < 4) return hhmm
        val h = hhmm.substring(0, 2).toIntOrNull() ?: return hhmm
        val m = hhmm.substring(2, 4).toIntOrNull() ?: return hhmm
        val totalM = h * 60 + m + 30
        val newH = (totalM / 60) % 24
        val newM = totalM % 60
        return String.format(Locale.US, "%02d:%02d", newH, newM)
    }
}

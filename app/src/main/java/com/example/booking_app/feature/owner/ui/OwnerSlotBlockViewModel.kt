package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerValidation
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.OwnerWriteGate
import com.example.booking_app.feature.owner.domain.SlotBlockInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class OwnerSlotItem(
    val slotId: String,
    val startTime: String,
    val endTime: String,
    val displayTime: String,
    val status: String,
    val blockReason: String? = null,
    val isSelected: Boolean = false,
    val isOwnerRemovable: Boolean = false
)

data class OwnerSlotBlockUiState(
    val venueId: String = "",
    val courtId: String = "",
    val selectedDate: String = "",
    val form: SlotBlockInput,
    val slotIds: List<String> = emptyList(),
    val gridSlots: List<OwnerSlotItem> = emptyList(),
    val selectedSlotIds: Set<String> = emptySet(),
    val selectedReason: String = "MAINTENANCE",
    val isLoadingGrid: Boolean = false,
    val isSubmitting: Boolean = false,
    val isStale: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val writeGate: OwnerWriteGate = OwnerWriteGate.SLOT_ATOMIC_SERVER_CONTRACT
)

@HiltViewModel
class OwnerSlotBlockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OwnerVenueRepository? = null
) : ViewModel() {
    private val courtIdArg: String = savedStateHandle["courtId"] ?: ""
    private val venueIdArg: String = savedStateHandle["venueId"] ?: ""
    private val todayString: String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    private val _uiState = MutableStateFlow(
        OwnerSlotBlockUiState(
            venueId = venueIdArg,
            courtId = courtIdArg,
            selectedDate = todayString,
            form = SlotBlockInput(courtIdArg, todayString, "", "", "MAINTENANCE")
        )
    )
    val uiState: StateFlow<OwnerSlotBlockUiState> = _uiState.asStateFlow()

    init {
        if (venueIdArg.isNotEmpty() && courtIdArg.isNotEmpty()) {
            loadGrid(venueIdArg, courtIdArg, todayString)
        }
    }

    fun updateForm(form: SlotBlockInput) {
        _uiState.value = _uiState.value.copy(form = form, error = null, message = null)
    }

    fun setOffline(value: Boolean) {
        _uiState.value = _uiState.value.copy(isOffline = value)
    }

    fun markStale() {
        _uiState.value = _uiState.value.copy(isStale = true)
    }

    fun retryAfterRefresh() {
        _uiState.value = _uiState.value.copy(isStale = false, error = null)
        val state = _uiState.value
        if (state.venueId.isNotEmpty() && state.courtId.isNotEmpty() && state.selectedDate.isNotEmpty()) {
            loadGrid(state.venueId, state.courtId, state.selectedDate)
        }
    }

    fun selectDate(date: String) {
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            form = _uiState.value.form.copy(date = date),
            selectedSlotIds = emptySet(),
            error = null
        )
        val state = _uiState.value
        if (state.venueId.isNotEmpty() && state.courtId.isNotEmpty()) {
            loadGrid(state.venueId, state.courtId, date)
        }
    }

    fun setReason(reason: String) {
        _uiState.value = _uiState.value.copy(
            selectedReason = reason,
            form = _uiState.value.form.copy(reason = reason)
        )
    }

    fun toggleSlotSelection(slotId: String) {
        val current = _uiState.value.selectedSlotIds.toMutableSet()
        if (current.contains(slotId)) {
            current.remove(slotId)
        } else {
            current.add(slotId)
        }
        val updatedGrid = _uiState.value.gridSlots.map { slot ->
            slot.copy(isSelected = current.contains(slot.slotId))
        }
        _uiState.value = _uiState.value.copy(
            selectedSlotIds = current,
            gridSlots = updatedGrid
        )
    }

    fun loadGrid(venueId: String, courtId: String, date: String) {
        if (repository == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingGrid = true, error = null)
            val res = repository.getSlots(venueId, date)
            res.fold(
                onSuccess = { slots ->
                    val busyMap = slots.filter { it.courtId == courtId }.associateBy { it.id }
                    val allowedOwnerReasons = setOf("MAINTENANCE", "EVENT", "WALK_IN")

                    // Generate half-hour intervals 06:00 to 22:00
                    val items = (360 until 1320 step 30).map { minutes ->
                        val startH = minutes / 60
                        val startM = minutes % 60
                        val endMin = minutes + 30
                        val endH = endMin / 60
                        val endM = endMin % 60

                        val startTime = "%02d%02d".format(startH, startM)
                        val endTime = "%02d%02d".format(endH, endM)
                        val display = "%02d:%02d - %02d:%02d".format(startH, startM, endH, endM)
                        val slotId = "${courtId}_${date}_$startTime"

                        val slotDoc = busyMap[slotId]
                        val status = slotDoc?.status ?: "AVAILABLE"
                        val reason = slotDoc?.blockReason
                        val isRemovable = status == "BLOCKED" && reason in allowedOwnerReasons
                        val isSelected = _uiState.value.selectedSlotIds.contains(slotId)

                        OwnerSlotItem(
                            slotId = slotId,
                            startTime = startTime,
                            endTime = endTime,
                            displayTime = display,
                            status = status,
                            blockReason = reason,
                            isSelected = isSelected,
                            isOwnerRemovable = isRemovable
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        gridSlots = items,
                        isLoadingGrid = false,
                        isStale = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingGrid = false,
                        error = e.message ?: "Không thể tải lưới khung giờ"
                    )
                }
            )
        }
    }

    fun preview() {
        val form = _uiState.value.form
        val errors = OwnerValidation.slotBlock(form)
        _uiState.value = if (errors.isNotEmpty()) {
            _uiState.value.copy(slotIds = emptyList(), error = errors.first().message)
        } else {
            _uiState.value.copy(slotIds = OwnerValidation.slotIds(form), error = null)
        }
    }

    fun submit() {
        if (_uiState.value.isSubmitting) return
        preview()
        if (_uiState.value.error != null) return

        val repo = repository
        if (repo == null) {
            _uiState.value = _uiState.value.copy(
                message = "Chưa thể khóa giờ: chờ hợp đồng server T007",
                isSubmitting = false,
                writeGate = OwnerWriteGate.SLOT_ATOMIC_SERVER_CONTRACT
            )
            return
        }

        val state = _uiState.value
        val venueId = state.venueId
        val courtId = state.form.courtId
        val date = state.form.date
        val slotIds = state.slotIds
        val reason = state.form.reason

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, message = null)
            val result = repo.lockSlots(venueId, courtId, date, slotIds, reason)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        message = "Đã khóa ${slotIds.size} khung giờ thành công",
                        selectedSlotIds = emptySet()
                    )
                    loadGrid(venueId, courtId, date)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = e.message ?: "Lỗi khi khóa khung giờ"
                    )
                }
            )
        }
    }

    fun lockSelectedSlots() {
        val state = _uiState.value
        if (state.isSubmitting || state.isOffline || repository == null) return
        val selected = state.selectedSlotIds.toList()
        if (selected.isEmpty()) {
            _uiState.value = state.copy(error = "Vui lòng chọn ít nhất một khung giờ trống để khóa")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, message = null)
            val result = repository.lockSlots(
                venueId = state.venueId,
                courtId = state.courtId,
                date = state.selectedDate,
                slotIds = selected,
                reason = state.selectedReason
            )
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        message = "Đã khóa ${selected.size} khung giờ thành công",
                        selectedSlotIds = emptySet()
                    )
                    loadGrid(state.venueId, state.courtId, state.selectedDate)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = e.message ?: "Lỗi khi khóa khung giờ"
                    )
                }
            )
        }
    }

    fun unlockSelectedSlots() {
        val state = _uiState.value
        if (state.isSubmitting || state.isOffline || repository == null) return
        val selected = state.selectedSlotIds.toList()
        if (selected.isEmpty()) {
            _uiState.value = state.copy(error = "Vui lòng chọn ít nhất một khung giờ đang khóa để mở")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, message = null)
            val result = repository.unlockSlots(
                venueId = state.venueId,
                courtId = state.courtId,
                slotIds = selected
            )
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        message = "Đã mở khóa ${selected.size} khung giờ thành công",
                        selectedSlotIds = emptySet()
                    )
                    loadGrid(state.venueId, state.courtId, state.selectedDate)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = e.message ?: "Lỗi khi mở khóa khung giờ"
                    )
                }
            )
        }
    }
}

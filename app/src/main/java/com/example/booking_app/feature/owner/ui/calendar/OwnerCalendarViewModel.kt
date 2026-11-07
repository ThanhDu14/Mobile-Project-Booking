package com.example.booking_app.feature.owner.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.calendar.OwnerCalendarRepository
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OwnerCalendarMode { DAY, WEEK }

data class OwnerCalendarUiState(
    val mode: OwnerCalendarMode = OwnerCalendarMode.DAY,
    val selectedDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),
    val venues: List<OwnerVenue> = emptyList(),
    val selectedVenueId: String? = null,
    val courts: List<OwnerCourt> = emptyList(),
    val slotsByDate: Map<LocalDate, List<OwnerSlot>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
)

@HiltViewModel
class OwnerCalendarViewModel @Inject constructor(
    private val repository: OwnerVenueRepository,
    private val calendarRepository: OwnerCalendarRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerCalendarUiState(isLoading = true))
    val uiState: StateFlow<OwnerCalendarUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { loadVenues() }

    fun refresh() {
        val state = _uiState.value
        if (state.selectedVenueId == null) loadVenues() else loadVenueCalendar(state.selectedVenueId)
    }

    fun selectVenue(venueId: String) {
        if (venueId == _uiState.value.selectedVenueId) return
        loadVenueCalendar(venueId)
    }

    fun setMode(mode: OwnerCalendarMode) {
        if (mode == _uiState.value.mode) return
        _uiState.value = _uiState.value.copy(mode = mode)
        refresh()
    }

    fun movePeriod(direction: Int) {
        if (direction == 0) return
        val state = _uiState.value
        val delta = if (state.mode == OwnerCalendarMode.DAY) direction.toLong() else direction * 7L
        _uiState.value = state.copy(selectedDate = state.selectedDate.plusDays(delta))
        refresh()
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        refresh()
    }

    private fun loadVenues() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getOwnedVenues()
                .onSuccess { venues ->
                    val selectedId = _uiState.value.selectedVenueId
                        ?.takeIf { id -> venues.any { it.id == id } }
                        ?: venues.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        venues = venues,
                        selectedVenueId = selectedId,
                        isLoading = selectedId != null,
                        error = null,
                        isEmpty = venues.isEmpty(),
                    )
                    if (selectedId == null) return@onSuccess
                    loadJob = null
                    loadVenueCalendar(selectedId)
                }
                .onFailure { failure ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = failure.message ?: "Không tải được danh sách cơ sở",
                        isEmpty = false,
                    )
                }
        }
    }

    private fun loadVenueCalendar(venueId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(
                selectedVenueId = venueId,
                isLoading = true,
                error = null,
                slotsByDate = emptyMap(),
                isEmpty = false,
            )
            val dates = state.dates()
            val courtsResult = repository.getCourts(venueId)
            if (courtsResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = courtsResult.exceptionOrNull()?.message ?: "Không tải được danh sách sân",
                )
                return@launch
            }
            val dateKeys = dates.map { it.format(SLOT_DATE_FORMAT) }
            try {
                calendarRepository.observeSlots(venueId, dateKeys).collect { slotsByKey ->
                    val slots = dates.associate { date -> date to slotsByKey[date.format(SLOT_DATE_FORMAT)].orEmpty() }
                    _uiState.value = _uiState.value.copy(
                        courts = courtsResult.getOrThrow().filter { it.isActive }.sortedBy { it.sortOrder },
                        slotsByDate = slots,
                        isLoading = false,
                        error = null,
                        isEmpty = slots.values.all { it.isEmpty() },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = failure.message ?: "Không tải được lịch sân",
                    isEmpty = false,
                )
            }
        }
    }

    private fun OwnerCalendarUiState.dates(): List<LocalDate> = when (mode) {
        OwnerCalendarMode.DAY -> listOf(selectedDate)
        OwnerCalendarMode.WEEK -> {
            val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            (0L..6L).map(monday::plusDays)
        }
    }

    private companion object {
        val SLOT_DATE_FORMAT = java.time.format.DateTimeFormatter.BASIC_ISO_DATE
    }
}

package com.example.booking_app.feature.owner.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsPeriod
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsQuery
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsRepository
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val OWNER_LOCAL_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

data class OwnerStatisticsUiState(
    val venues: List<OwnerVenue> = emptyList(),
    val selectedVenueId: String? = null,
    val period: OwnerStatisticsPeriod = OwnerStatisticsPeriod.DAY,
    val selectedDate: LocalDate = LocalDate.now(OWNER_LOCAL_ZONE),
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val result: OwnerStatisticsResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
)

@HiltViewModel
class OwnerStatisticsViewModel @Inject constructor(
    private val venueRepository: OwnerVenueRepository,
    private val statisticsRepository: OwnerStatisticsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerStatisticsUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private var observationJob: Job? = null

    init { loadVenues() }

    fun selectVenue(venueId: String) {
        _uiState.value = _uiState.value.copy(selectedVenueId = venueId)
        loadReport()
    }

    fun setPeriod(period: OwnerStatisticsPeriod) {
        _uiState.value = _uiState.value.copy(period = period)
        loadReport()
    }

    fun movePeriod(direction: Int) {
        if (direction == 0) return
        val state = _uiState.value
        val moved = when (state.period) {
            OwnerStatisticsPeriod.DAY -> state.selectedDate.plusDays(direction.toLong())
            OwnerStatisticsPeriod.WEEK -> state.selectedDate.plusWeeks(direction.toLong())
            OwnerStatisticsPeriod.CUSTOM -> return
        }
        _uiState.value = state.copy(selectedDate = moved)
        loadReport()
    }

    fun setCustomRange(start: LocalDate?, endInclusive: LocalDate?) {
        _uiState.value = _uiState.value.copy(
            period = OwnerStatisticsPeriod.CUSTOM,
            customStartDate = start,
            customEndDate = endInclusive,
            error = if (start == null || endInclusive == null || start.isAfter(endInclusive)) "Khoảng ngày không hợp lệ." else null,
        )
        if (start != null && endInclusive != null && !start.isAfter(endInclusive)) loadReport()
    }

    fun refresh() = if (_uiState.value.selectedVenueId == null) loadVenues() else loadReport()

    private fun loadVenues() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            venueRepository.getOwnedVenues()
                .onSuccess { venues ->
                    val selected = _uiState.value.selectedVenueId?.takeIf { id -> venues.any { it.id == id } } ?: venues.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(venues = venues, selectedVenueId = selected, isLoading = false, isEmpty = venues.isEmpty())
                    selected?.let { loadReport() }
                }
                .onFailure { failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = failure.message ?: "Không tải được cơ sở", isEmpty = false) }
        }
    }

    private fun loadReport() {
        val state = _uiState.value
        val venueId = state.selectedVenueId ?: return
        val range = queryRange(state) ?: return
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, result = null, isEmpty = false)
            try {
                statisticsRepository.observe(venueId, range).collect { result ->
                    _uiState.value = _uiState.value.copy(result = result, isLoading = false, error = null, isEmpty = result == null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = failure.message ?: "Không tải được thống kê", isEmpty = false)
            }
        }
    }

    private fun queryRange(state: OwnerStatisticsUiState): OwnerStatisticsQuery? {
        val (start, end) = when (state.period) {
            OwnerStatisticsPeriod.DAY -> state.selectedDate to state.selectedDate
            OwnerStatisticsPeriod.WEEK -> {
                val monday = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                monday to monday.plusDays(6)
            }
            OwnerStatisticsPeriod.CUSTOM -> (state.customStartDate ?: return null) to (state.customEndDate ?: return null)
        }
        return OwnerStatisticsQuery(start, end)
    }

}

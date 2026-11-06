package com.example.booking_app.feature.owner.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.booking.OwnerBooking
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingCommand
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingDetail
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingFilter
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingQueue
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingRepository
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerBookingsUiState(
    val venues: List<OwnerVenue> = emptyList(),
    val selectedVenueId: String? = null,
    val filter: OwnerBookingFilter = OwnerBookingFilter.PENDING,
    val allBookings: List<OwnerBooking> = emptyList(),
    val bookings: List<OwnerBooking> = emptyList(),
    val selectedDetail: OwnerBookingDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
    val actionInProgress: Boolean = false,
    val actionMessage: String? = null,
    val now: Instant = Instant.now(Clock.systemUTC()),
)

@HiltViewModel
class OwnerBookingsViewModel @Inject constructor(
    private val venueRepository: OwnerVenueRepository,
    private val bookingRepository: OwnerBookingRepository,
) : ViewModel() {
    private val queue = OwnerBookingQueue()
    private val _uiState = MutableStateFlow(OwnerBookingsUiState(isLoading = true))
    val uiState: StateFlow<OwnerBookingsUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null
    private var tickerJob: Job? = null

    init {
        loadVenues()
    }

    fun startCountdownTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _uiState.value = _uiState.value.copy(now = Instant.now(Clock.systemUTC())).withFilteredBookings()
            }
        }
    }

    fun stopCountdownTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun refresh() {
        val venueId = _uiState.value.selectedVenueId
        if (venueId == null) loadVenues() else observeBookings(venueId)
    }

    fun selectVenue(venueId: String) {
        if (venueId != _uiState.value.selectedVenueId) observeBookings(venueId)
    }

    fun selectFilter(filter: OwnerBookingFilter) {
        val state = _uiState.value.copy(filter = filter)
        _uiState.value = state.withFilteredBookings()
    }

    fun showDetails(bookingId: String) {
        val state = _uiState.value
        val booking = state.allBookings.firstOrNull { it.bookingId == bookingId } ?: return
        viewModelScope.launch {
            bookingRepository.getBooking(booking.venueId, booking.bookingId)
                .onSuccess { detail -> _uiState.value = _uiState.value.copy(selectedDetail = detail, error = null) }
                .onFailure { failure ->
                    _uiState.value = _uiState.value.copy(error = failure.message ?: "Không tải được chi tiết đơn")
                }
        }
    }

    fun clearDetails() {
        _uiState.value = _uiState.value.copy(selectedDetail = null)
    }

    fun submit(command: OwnerBookingCommand) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = true, actionMessage = null, error = null)
            bookingRepository.submit(command)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(actionInProgress = false, actionMessage = "Đã cập nhật booking")
                    refresh()
                }
                .onFailure { failure ->
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = false,
                        error = failure.message ?: "Chưa thể xử lý booking",
                    )
                }
        }
    }

    private fun loadVenues() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            venueRepository.getOwnedVenues()
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
                    observationJob = null
                    observeBookings(selectedId)
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

    private fun observeBookings(venueId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedVenueId = venueId,
                isLoading = true,
                error = null,
                allBookings = emptyList(),
                bookings = emptyList(),
                selectedDetail = null,
                isEmpty = false,
            )
            try {
                bookingRepository.observeBookings(venueId).collect { bookings ->
                    _uiState.value = _uiState.value.copy(
                        allBookings = bookings,
                        isLoading = false,
                        error = null,
                    ).withFilteredBookings()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = failure.message ?: "Không tải được danh sách booking",
                    isEmpty = false,
                )
            }
        }
    }

    private fun OwnerBookingsUiState.withFilteredBookings(): OwnerBookingsUiState {
        val filtered = queue.filter(allBookings, filter, now)
        return copy(bookings = filtered, isEmpty = !isLoading && error == null && filtered.isEmpty())
    }
}

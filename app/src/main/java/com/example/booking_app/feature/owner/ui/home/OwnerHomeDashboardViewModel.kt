package com.example.booking_app.feature.owner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.core.session.initialsOf
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingRepository
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingStatus
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRepository
import com.example.booking_app.feature.owner.domain.calendar.OwnerCalendarRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class OwnerUpcomingEvent(val startAt: Instant, val endAt: Instant, val title: String, val subtitle: String)

data class OwnerHomeDashboardState(
    val venues: List<OwnerVenue> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
    val selectedVenueId: String? = null,
    val pendingCount: Int = 0,
    val nextDeadline: Instant? = null,
    val upcoming: List<OwnerUpcomingEvent> = emptyList(),
)

@HiltViewModel
class OwnerHomeDashboardViewModel @Inject constructor(
    profileReader: UserProfileReader,
    private val venueRepository: OwnerVenueRepository,
    private val bookingRepository: OwnerBookingRepository,
    private val dropInRepository: OwnerDropInRepository,
    private val calendarRepository: OwnerCalendarRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OwnerHomeDashboardState(isLoading = true))
    val state: StateFlow<OwnerHomeDashboardState> = _state.asStateFlow()
    private var loadJob: Job? = null

    val initials: StateFlow<String> = profileReader.observeMyProfile()
        .map { profile -> profile?.displayName?.let(::initialsOf).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init { refresh() }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val venues = venueRepository.getOwnedVenues().getOrThrow()
            val venue = venues.firstOrNull { it.id == _state.value.selectedVenueId } ?: venues.firstOrNull()
            if (venue == null) {
                _state.value = OwnerHomeDashboardState(isEmpty = true)
                return@launch
            }
            val zone = ZoneId.of("Asia/Ho_Chi_Minh")
            val today = LocalDate.now(zone)
            val date = today.format(DateTimeFormatter.BASIC_ISO_DATE)
            combine(bookingRepository.observeBookings(venue.id), dropInRepository.observeSessions(venue.id),
                calendarRepository.observeSlots(venue.id, listOf(date))) { bookings, sessions, slots ->
                val now = Instant.now()
                val pending = bookings.filter { it.status == OwnerBookingStatus.PENDING && it.approvalDeadline?.isAfter(now) == true }
                val upcoming = bookings.filter { it.status == OwnerBookingStatus.CONFIRMED && it.endAt > now && it.startAt.atZone(zone).toLocalDate() == today }
                    .map { OwnerUpcomingEvent(it.startAt, it.endAt, it.customerDisplayName ?: it.bookingId, it.courtNames.joinToString(" · ")) } +
                    sessions.filter { it.status in setOf("OPEN", "FULL") && it.endAt > now && it.startAt.atZone(zone).toLocalDate() == today }
                        .map { OwnerUpcomingEvent(it.startAt, it.endAt, "Buổi vãng lai", "${it.registeredCount}/${it.capacity} người") } +
                    slots[date].orEmpty().filter { it.status == "BLOCKED" && it.blockReason != "DROP_IN" }
                        .mapNotNull { slot ->
                            val start = slot.startAt?.toDate()?.toInstant() ?: return@mapNotNull null
                            val end = slot.endAt?.toDate()?.toInstant() ?: return@mapNotNull null
                            if (end <= now) null else OwnerUpcomingEvent(start, end, when (slot.blockReason) {
                                "MAINTENANCE" -> "Bảo trì"; "EVENT" -> "Sự kiện"; else -> "Khách tại sân"
                            }, slot.courtId)
                        }
                OwnerHomeDashboardState(venues = venues, selectedVenueId = venue.id, pendingCount = pending.size,
                    nextDeadline = pending.mapNotNull { it.approvalDeadline }.minOrNull(), upcoming = upcoming.sortedBy { it.startAt })
            }.collect { _state.value = it }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = failure.message ?: "Không tải được thông tin cơ sở", pendingCount = 0, upcoming = emptyList())
        }
        }
    }

    fun selectVenue(id: String) {
        if (_state.value.selectedVenueId == id) return
        _state.value = _state.value.copy(selectedVenueId = id)
        refresh()
    }
}

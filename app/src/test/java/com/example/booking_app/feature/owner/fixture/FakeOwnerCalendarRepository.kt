package com.example.booking_app.feature.owner.fixture

import com.example.booking_app.feature.owner.domain.calendar.OwnerCalendarRepository
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Lightweight deterministic fake for calendar query and repository-error tests. */
class FakeOwnerCalendarRepository(
    private val slotsByDate: Map<String, List<OwnerSlot>> = emptyMap(),
    private val failure: Throwable? = null,
) : OwnerCalendarRepository {
    val observedQueries = mutableListOf<Pair<String, List<String>>>()

    override fun observeSlots(venueId: String, dates: List<String>): Flow<Map<String, List<OwnerSlot>>> = flow {
        observedQueries += venueId to dates.toList()
        failure?.let { throw it }
        emit(slotsByDate.filterKeys { it in dates })
    }
}

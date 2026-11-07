package com.example.booking_app.feature.owner.domain.calendar

import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import kotlinx.coroutines.flow.Flow

/** Realtime view of public availability slots for dates selected by the signed-in owner. */
interface OwnerCalendarRepository {
    fun observeSlots(venueId: String, dates: List<String>): Flow<Map<String, List<OwnerSlot>>>
}

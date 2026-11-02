package com.example.booking_app.feature.booking.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class RecurringSession(
    val date: LocalDate,
    val dayOfWeek: Int = date.dayOfWeek.value, // ISO: 1 = Monday ... 7 = Sunday
    val startTime: String,                     // HHmm, e.g. "1800"
    val endTime: String,                       // HHmm, e.g. "2000"
    val courtId: String,
    val courtName: String,
    val surfaceType: String = "PU",
    val slotIds: List<String>,
    val price: Long,
    val status: RecurringSessionStatus = RecurringSessionStatus.AVAILABLE,
    val originalCourtId: String = courtId,
    val suggestedCourts: List<Court> = emptyList(),
    val isExcluded: Boolean = false
) {
    val dateString: String get() = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    val isCourtChanged: Boolean get() = courtId != originalCourtId
}

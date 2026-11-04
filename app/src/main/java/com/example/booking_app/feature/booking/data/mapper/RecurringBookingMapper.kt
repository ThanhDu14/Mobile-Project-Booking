package com.example.booking_app.feature.booking.data.mapper

import com.example.booking_app.feature.booking.data.model.BookingSessionDto
import com.example.booking_app.feature.booking.data.model.RecurringGroupDto
import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.model.RecurringSummary
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID

object RecurringBookingMapper {

    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun toRecurringGroupDto(
        recurringGroupId: String,
        config: RecurringConfig,
        venueName: String,
        sessions: List<RecurringSession>,
        summary: RecurringSummary,
        userId: String,
        requestId: String,
        holdExpiresAtMillis: Long,
        createdAtMillis: Long = System.currentTimeMillis()
    ): RecurringGroupDto {
        val validSessions = sessions.filter { !it.isExcluded && it.status != RecurringSessionStatus.MUST_SKIP }
        val startDateStr = validSessions.minOfOrNull { it.date }?.format(DATE_FORMATTER) ?: ""
        val endDateStr = validSessions.maxOfOrNull { it.date }?.format(DATE_FORMATTER) ?: ""

        return RecurringGroupDto(
            id = recurringGroupId,
            userId = userId,
            venueId = config.venueId,
            venueName = venueName,
            daysOfWeek = config.daysOfWeek,
            startTime = config.startTime,
            endTime = config.endTime,
            startDate = startDateStr,
            endDate = endDateStr,
            totalSessions = summary.validSessions.size,
            subtotal = summary.subtotalAmount,
            discount = 0L,
            total = summary.subtotalAmount,
            depositAmount = summary.depositAmount,
            paymentStatus = "PENDING",
            status = "HOLD",
            holdExpiresAt = Timestamp(Date(holdExpiresAtMillis)),
            requestId = requestId,
            createdAt = Timestamp(Date(createdAtMillis))
        )
    }

    fun toBookingSessionDto(
        session: RecurringSession,
        recurringGroupId: String,
        userId: String,
        venueId: String,
        bookingId: String = UUID.randomUUID().toString()
    ): BookingSessionDto {
        val depositAmount = ((session.price * 30L + 500L) / 1000L) * 1000L / 100L
        return BookingSessionDto(
            id = bookingId,
            recurringGroupId = recurringGroupId,
            userId = userId,
            venueId = venueId,
            courtId = session.courtId,
            courtName = session.courtName,
            date = session.date.format(DATE_FORMATTER),
            startTime = session.startTime,
            endTime = session.endTime,
            slotIds = session.slotIds,
            status = "HOLD",
            price = session.price,
            depositAmount = depositAmount
        )
    }

    fun toDomainSession(dto: BookingSessionDto, surfaceType: String = ""): RecurringSession {
        val date = LocalDate.parse(dto.date, DATE_FORMATTER)
        val status = when (dto.status.uppercase()) {
            "HOLD", "HELD" -> RecurringSessionStatus.HELD
            "MUST_SKIP" -> RecurringSessionStatus.MUST_SKIP
            "EXCLUDED" -> RecurringSessionStatus.EXCLUDED
            "SUGGEST_CHANGE_COURT" -> RecurringSessionStatus.SUGGEST_CHANGE_COURT
            else -> RecurringSessionStatus.AVAILABLE
        }
        return RecurringSession(
            date = date,
            dayOfWeek = date.dayOfWeek.value,
            startTime = dto.startTime,
            endTime = dto.endTime,
            courtId = dto.courtId,
            courtName = dto.courtName,
            surfaceType = surfaceType,
            slotIds = dto.slotIds,
            price = dto.price,
            status = status,
            originalCourtId = dto.courtId,
            suggestedCourts = emptyList(),
            isExcluded = status == RecurringSessionStatus.EXCLUDED
        )
    }
}

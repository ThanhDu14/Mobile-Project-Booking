package com.example.booking_app.feature.booking.data.mapper

import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.google.firebase.Timestamp
import java.util.Date

object BookingSlotMapper {

    fun toDomain(dto: CourtDto): Court = Court(
        id = dto.id.ifEmpty { "unknown" },
        name = dto.name.ifEmpty { "Sân" },
        surfaceType = dto.surfaceType.ifEmpty { "MAT" },
        isActive = dto.isActive,
        sortOrder = dto.sortOrder
    )

    fun toDto(domain: Court): CourtDto = CourtDto(
        id = domain.id,
        name = domain.name,
        surfaceType = domain.surfaceType,
        isActive = domain.isActive,
        sortOrder = domain.sortOrder
    )

    fun toDomain(dto: PriceRuleDto): PriceRule = PriceRule(
        id = dto.id.ifEmpty { "rule" },
        daysOfWeek = dto.daysOfWeek,
        startTime = dto.startTime.padStart(4, '0'),
        endTime = dto.endTime.padStart(4, '0'),
        pricePerHour = dto.pricePerHour.coerceAtLeast(0L),
        label = dto.label.ifEmpty { "NORMAL" }
    )

    fun toDto(domain: PriceRule): PriceRuleDto = PriceRuleDto(
        id = domain.id,
        daysOfWeek = domain.daysOfWeek,
        startTime = domain.startTime,
        endTime = domain.endTime,
        pricePerHour = domain.pricePerHour,
        label = domain.label
    )

    fun toDomain(dto: SlotDto, calculatedPrice: Long = 0L): TimeSlot {
        val status = parseStatus(dto.status, dto.holdExpiresAt)
        return TimeSlot(
            slotId = dto.id,
            courtId = dto.courtId,
            date = dto.date,
            startTime = dto.startTime.padStart(4, '0'),
            endTime = dto.endTime.padStart(4, '0'),
            price = calculatedPrice,
            status = status,
            blockReason = dto.blockReason,
            holdExpiresAtMillis = dto.holdExpiresAt?.toDate()?.time
        )
    }

    fun toDto(domain: TimeSlot, venueId: String, holdBy: String? = null, bookingId: String? = null): SlotDto = SlotDto(
        id = domain.slotId,
        venueId = venueId,
        courtId = domain.courtId,
        date = domain.date,
        startTime = domain.startTime,
        endTime = domain.endTime,
        status = domain.status.name,
        blockReason = domain.blockReason,
        holdExpiresAt = domain.holdExpiresAtMillis?.let { Timestamp(Date(it)) },
        holdBy = holdBy,
        bookingId = bookingId
    )

    private fun parseStatus(statusStr: String?, holdExpiresAt: Timestamp?): SlotStatus {
        val now = System.currentTimeMillis()
        return when (statusStr?.uppercase()) {
            "BOOKED" -> SlotStatus.BOOKED
            "BLOCKED" -> SlotStatus.BLOCKED
            "HELD" -> {
                val expiresAtMillis = holdExpiresAt?.toDate()?.time ?: 0L
                if (expiresAtMillis > now) {
                    SlotStatus.HELD
                } else {
                    SlotStatus.AVAILABLE // Expired hold is treated as available (Constitution III)
                }
            }
            "PAST" -> SlotStatus.PAST
            else -> SlotStatus.AVAILABLE
        }
    }
}

package com.example.booking_app.feature.dropin.ui

import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import java.time.Instant

/** Mốc "bây giờ" cố định cho test UI vãng lai: 2026-11-11T03:00:00Z (10:00 giờ Việt Nam). */
val NOW: Instant = Instant.parse("2026-11-11T03:00:00Z")

fun session(
    id: String = "s1",
    startAt: Instant = NOW.plusSeconds(3 * 3600L),
    capacity: Int = 12,
    registeredCount: Int = 7,
    pricePerPerson: Long = 60_000,
    status: SessionStatus = SessionStatus.OPEN,
    ownerId: String = "owner1",
    venueActive: Boolean = true,
    level: Level? = Level.INTERMEDIATE,
    districtId: String = "q10",
) = DropInSession(
    id = id,
    venueId = "v1",
    ownerId = ownerId,
    districtId = districtId,
    venueName = "Sân cầu lông Hòa Bình",
    venueAddress = "245 Ba Tháng Hai",
    districtName = "Quận 10",
    courtIds = listOf("c1", "c2"),
    courtNames = listOf("Sân 1", "Sân 2"),
    startAt = startAt,
    endAt = startAt.plusSeconds(2 * 3600L),
    timeSlot = TimeSlot.EVENING,
    level = level,
    capacity = capacity,
    registeredCount = registeredCount,
    pricePerPerson = pricePerPerson,
    status = status,
    note = "Mang giày đế cao su",
    venueActive = venueActive,
)

fun registration(
    sessionId: String = "s1",
    userId: String = "u1",
    partySize: Int = 2,
    status: RegistrationStatus = RegistrationStatus.REGISTERED,
    paymentMethod: PaymentMethod = PaymentMethod.MOCK_PAY,
    paymentStatus: PaymentStatus = PaymentStatus.PAID,
    qrToken: String? = "qr-token-123",
    sessionChanged: Boolean = false,
) = DropInRegistration(
    sessionId = sessionId,
    userId = userId,
    partySize = partySize,
    status = status,
    paymentMethod = paymentMethod,
    paymentStatus = paymentStatus,
    pricePerPerson = 60_000,
    amount = 60_000L * partySize,
    refundAmount = null,
    qrToken = qrToken,
    ticketCode = "DI-7K2P",
    sessionChanged = sessionChanged,
    sessionStartAt = NOW.plusSeconds(3 * 3600L),
    sessionEndAt = NOW.plusSeconds(5 * 3600L),
    registeredAt = NOW,
    cancelledBy = null,
)

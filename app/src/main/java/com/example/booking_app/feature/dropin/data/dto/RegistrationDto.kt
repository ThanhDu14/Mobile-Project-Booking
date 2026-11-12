package com.example.booking_app.feature.dropin.data.dto

import java.time.Instant

/** Document `dropInSessions/{sid}/registrations/{uid}` đã đọc thô (data-model mục 2). */
data class RegistrationDto(
    val sessionId: String,
    val userId: String,
    val partySize: Long? = null,
    val status: String? = null,
    val paymentMethod: String? = null,
    val paymentStatus: String? = null,
    val pricePerPerson: Long? = null,
    val amount: Long? = null,
    val refundAmount: Long? = null,
    val qrToken: String? = null,
    val ticketCode: String? = null,
    val sessionChanged: Boolean? = null,
    val sessionStartAt: Instant? = null,
    val sessionEndAt: Instant? = null,
    val registeredAt: Instant? = null,
    val cancelledBy: String? = null,
) {
    companion object {
        /**
         * [parentSessionId], [docId] là ID buổi cha và ID document (= uid); dùng khi document thiếu
         * `sessionId`/`userId`.
         */
        fun fromMap(parentSessionId: String, docId: String, data: Map<String, Any?>): RegistrationDto =
            RegistrationDto(
                sessionId = data.string("sessionId") ?: parentSessionId,
                userId = data.string("userId") ?: docId,
                partySize = data["partySize"].asLong(),
                status = data.string("status"),
                paymentMethod = data.string("paymentMethod"),
                paymentStatus = data.string("paymentStatus"),
                pricePerPerson = data["pricePerPerson"].asLong(),
                amount = data["amount"].asLong(),
                refundAmount = data["refundAmount"].asLong(),
                qrToken = data.string("qrToken"),
                ticketCode = data.string("ticketCode"),
                sessionChanged = data["sessionChanged"] as? Boolean,
                sessionStartAt = data["sessionStartAt"].asInstant(),
                sessionEndAt = data["sessionEndAt"].asInstant(),
                registeredAt = data["registeredAt"].asInstant(),
                cancelledBy = data.string("cancelledBy"),
            )
    }
}

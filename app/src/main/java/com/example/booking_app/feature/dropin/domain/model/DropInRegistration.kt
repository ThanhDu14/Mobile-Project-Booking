package com.example.booking_app.feature.dropin.domain.model

import java.time.Instant

/** Đăng ký của chính người chơi: `dropInSessions/{sessionId}/registrations/{uid}` (data-model mục 2). */
data class DropInRegistration(
    val sessionId: String,
    val userId: String,
    val partySize: Int,
    val status: RegistrationStatus,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus,
    val pricePerPerson: Long,
    val amount: Long,
    val refundAmount: Long?,
    /** Chỉ có khi đọc từ Firestore; response của Edge Function không trả token này. */
    val qrToken: String?,
    val ticketCode: String?,
    val sessionChanged: Boolean,
    val sessionStartAt: Instant?,
    val sessionEndAt: Instant?,
    val registeredAt: Instant?,
    val cancelledBy: CancelledBy?,
) {
    /** Còn hiệu lực: đã đăng ký hoặc đã check-in (AC 2.7). */
    val isActive: Boolean
        get() = status == RegistrationStatus.REGISTERED || status == RegistrationStatus.CHECKED_IN
}

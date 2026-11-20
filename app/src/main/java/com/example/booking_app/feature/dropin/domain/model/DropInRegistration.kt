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
    /** Số vé chờ, quyết định thứ tự (spec 081 FR-004); `null` với đăng ký không qua hàng chờ. */
    val waitlistTicket: Int? = null,
    /** Có giá trị khi được đẩy lên từ hàng chờ: nhãn "Được nhận từ hàng chờ" và hạn 30 phút hoàn 100% (FR-011). */
    val promotedAt: Instant? = null,
    /** Có giá trị khi lượt chờ kết thúc mà không được nhận (research R6). */
    val waitlistEndReason: WaitlistEndReason? = null,
) {
    val isFromWaitlist: Boolean get() = promotedAt != null

    /** Còn hiệu lực: đã đăng ký hoặc đã check-in (AC 2.7). */
    val isActive: Boolean
        get() = status == RegistrationStatus.REGISTERED || status == RegistrationStatus.CHECKED_IN
}

/** Trạng thái của lượt nằm ở tab "Sắp tới". */
val UPCOMING_REGISTRATION_STATUSES = setOf(RegistrationStatus.REGISTERED, RegistrationStatus.WAITLISTED)

/**
 * Lượt nằm ở tab "Sắp tới" cho tới khi buổi kết thúc, kể cả lúc buổi đang diễn ra, để người đến trễ vẫn
 * tìm thấy vé check-in. Mỗi lượt ở "Đã qua" vì thế luôn có nhãn kết quả (FR-019). Thiếu giờ kết thúc thì
 * tính theo giờ bắt đầu.
 */
fun DropInRegistration.isUpcomingAt(now: Instant): Boolean {
    if (status !in UPCOMING_REGISTRATION_STATUSES) return false
    val end = sessionEndAt ?: sessionStartAt ?: return false
    return !end.isBefore(now)
}

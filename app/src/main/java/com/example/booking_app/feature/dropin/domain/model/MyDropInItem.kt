package com.example.booking_app.feature.dropin.domain.model

import java.time.Instant

/**
 * Một lượt trong "Lượt vãng lai của tôi" (US3, research R7): đăng ký của mình kèm buổi đọc theo lô để hiển thị
 * tên cơ sở, giờ mới, trạng thái buổi. [session] là `null` khi không đọc được buổi (đã xóa, mất mạng chưa có cache).
 */
data class MyDropInItem(
    val registration: DropInRegistration,
    val session: DropInSession?,
    val outcome: RegistrationOutcome,
) {
    val sessionId: String get() = registration.sessionId

    /** Giờ của buổi (giờ mới nếu chủ sân đã đổi), không có buổi thì dùng giờ sao trong đăng ký. */
    val startAt: Instant? get() = session?.startAt ?: registration.sessionStartAt

    val endAt: Instant? get() = session?.endAt ?: registration.sessionEndAt
}

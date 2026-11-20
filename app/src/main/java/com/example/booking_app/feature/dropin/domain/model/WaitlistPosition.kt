package com.example.booking_app.feature.dropin.domain.model

/**
 * Vị trí của một lượt trong hàng chờ (spec 081 data-model mục 5): [position] = chỉ số của số vé trong
 * `session.waitlistTickets` cộng 1, [total] = số lượt đang chờ. Chỉ có số, không có danh tính (FR-006).
 */
data class WaitlistPosition(val position: Int, val total: Int) {
    companion object {
        /** `null` khi lượt không có số vé, mảng rỗng hoặc số vé không còn trong mảng (đã rời, đã được đẩy lên). */
        fun of(session: DropInSession, registration: DropInRegistration): WaitlistPosition? {
            val ticket = registration.waitlistTicket ?: return null
            val index = session.waitlistTickets.indexOf(ticket)
            if (index < 0) return null
            return WaitlistPosition(position = index + 1, total = session.waitlistTickets.size)
        }
    }
}

/** Kết quả vào hàng chờ: lượt chờ vừa tạo và vị trí server trả về (contract mục 1). */
data class WaitlistJoinResult(
    val registration: DropInRegistration,
    val position: WaitlistPosition,
)

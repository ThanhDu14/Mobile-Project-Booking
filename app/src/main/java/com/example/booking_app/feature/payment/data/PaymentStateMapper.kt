package com.example.booking_app.feature.payment.data

import com.google.firebase.Timestamp

/** Đọc trạng thái thanh toán từ document đơn (`bookings/{id}`) hoặc nhóm (`recurringGroups/{id}`). Hàm thuần. */
internal object PaymentStateMapper {

    sealed interface Kind {
        data class Awaiting(val holdExpiresAtMillis: Long) : Kind
        data object Confirmed : Kind
        data object Expired : Kind
    }

    /**
     * Đơn lẻ: rời `HOLD` (sang `PENDING` hoặc xa hơn) là đã chốt. Nhóm định kỳ: `DONE` là đã chốt.
     * `EXPIRED` là hết hạn; document chưa có (hoặc thiếu hạn) thì chưa kết luận được → `null`.
     */
    fun kind(data: Map<String, Any?>?, isRecurring: Boolean): Kind? {
        val status = data?.get("status") as? String ?: return null
        return when {
            status == "EXPIRED" -> Kind.Expired
            status == "HOLD" -> millis(data["holdExpiresAt"])?.let { Kind.Awaiting(it) }
            isRecurring && status != "DONE" -> null
            else -> Kind.Confirmed
        }
    }

    private fun millis(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is java.util.Date -> value.time
        is Number -> value.toLong()
        else -> null
    }
}

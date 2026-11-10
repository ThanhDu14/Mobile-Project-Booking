package com.example.booking_app.feature.booking_management.data

import com.example.booking_app.feature.booking_management.domain.*
import com.google.firebase.Timestamp

/** Strictly reads persisted values; absent or malformed required data is never invented. */
internal object BookingManagementMapper {
    fun map(id: String, data: Map<String, Any?>, owner: String): BookingDetail {
        fun text(m: Map<*, *>, key: String) = (m[key] as? String)?.takeIf { it.isNotBlank() }
            ?: error("Missing $key")
        fun amount(m: Map<*, *>, key: String): Long = (m[key] as? Long)?.takeIf { it >= 0 }
            ?: error("Missing or invalid $key")
        fun time(m: Map<*, *>, key: String): Long = (m[key] as? Timestamp)?.toDate()?.time
            ?: error("Missing $key")
        fun optionalTime(key: String): Long? = data[key]?.let { time(data, key) }
        fun optionalAmount(key: String): Long? = data[key]?.let { amount(data, key) }
        require(text(data, "userId") == owner) { "Booking owner mismatch" }
        val snapshot = data["venueSnapshot"] as? Map<*, *> ?: error("Missing venueSnapshot")
        val items = (data["items"] as? List<*>)?.map { raw ->
            val item = raw as? Map<*, *> ?: error("Invalid item")
            val start = time(item, "startAt")
            val end = time(item, "endAt")
            require(end > start)
            BookingSlotItem(text(item, "slotId"), text(item, "courtId"), text(item, "courtName"), start, end, amount(item, "price"))
        }?.takeIf { it.isNotEmpty() } ?: error("Missing items")
        val start = time(data, "startAt")
        val end = time(data, "endAt")
        require(start == items.minOf { it.startAtMillis } && end == items.maxOf { it.endAtMillis })
        val subtotal = amount(data, "subtotal")
        val discount = amount(data, "discount")
        val total = amount(data, "total")
        val deposit = amount(data, "depositAmount")
        // These are not currently guaranteed by the shared schema. Do not infer them from status.
        val paid = amount(data, "paidAmount")
        val remaining = amount(data, "remainingAmount")
        require(discount <= subtotal && total == subtotal - discount && deposit <= total && paid <= total && remaining <= total)
        return BookingDetail(
            bookingId = id, userId = owner, venueId = text(data, "venueId"),
            venueSnapshot = VenueSnapshot(text(snapshot, "name"), text(snapshot, "address"), snapshot["phone"] as? String),
            items = items, startAtMillis = start, endAtMillis = end,
            status = RefundPolicy.Status.valueOf(text(data, "status")),
            finance = BookingFinance(subtotal, discount, total, deposit, paid, remaining,
                RefundPolicy.PaymentMethod.valueOf(text(data, "paymentMethod")),
                PaymentStatus.valueOf(text(data, "paymentStatus")), optionalAmount("refundAmount"), optionalAmount("penaltyAmount")),
            recurringGroupId = data["recurringGroupId"] as? String, qrToken = data["qrToken"] as? String,
            checkedInAtMillis = optionalTime("checkedInAt"), holdExpiresAtMillis = optionalTime("holdExpiresAt"),
            approvalDeadlineMillis = optionalTime("approvalDeadline"), cancelReason = data["cancelReason"] as? String,
            cancelledBy = data["cancelledBy"] as? String, cancelledAtMillis = optionalTime("cancelledAt"),
            completedAtMillis = optionalTime("completedAt"), createdAtMillis = time(data, "createdAt"), updatedAtMillis = time(data, "updatedAt")
        )
    }
}

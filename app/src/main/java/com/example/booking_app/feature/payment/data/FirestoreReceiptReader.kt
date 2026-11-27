package com.example.booking_app.feature.payment.data

import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.PaymentStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Đọc lại đơn đã chốt để hiện hóa đơn (T033). Đơn lẻ: `bookings/{id}`. Đơn định kỳ: tiền ở `recurringGroups/{id}`,
 * dòng chi tiết lấy từ các buổi `bookings` có `recurringGroupId` (lọc thêm `userId` để khớp Rules).
 */
class FirestoreReceiptReader @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    suspend fun read(bookingId: String, isRecurring: Boolean): PaymentReceipt {
        if (!isRecurring) {
            val doc = firestore.collection("bookings").document(bookingId).get().await()
            val data = requireNotNull(doc.data) { "Không tìm thấy đơn $bookingId" }
            @Suppress("UNCHECKED_CAST")
            return toReceipt(
                bookingId, null, data,
                ((data["venueSnapshot"] as? Map<String, Any?>)?.get("name") as? String).orEmpty(),
                ReceiptLines.of(data["items"] as? List<Map<String, Any?>> ?: emptyList()),
            )
        }
        val uid = requireNotNull(auth.currentUser?.uid) { "Chưa đăng nhập" }
        val group = requireNotNull(firestore.collection("recurringGroups").document(bookingId).get().await().data) {
            "Không tìm thấy nhóm $bookingId"
        }
        val sessions = firestore.collection("bookings")
            .whereEqualTo("recurringGroupId", bookingId)
            .whereEqualTo("userId", uid)
            .get().await().documents.mapNotNull { it.data }
        @Suppress("UNCHECKED_CAST")
        val items = sessions.flatMap { it["items"] as? List<Map<String, Any?>> ?: emptyList() }
        @Suppress("UNCHECKED_CAST")
        val venueName = sessions.firstNotNullOfOrNull { (it["venueSnapshot"] as? Map<String, Any?>)?.get("name") as? String }
            ?: group["venueName"] as? String
        return toReceipt(bookingId, bookingId, group, venueName.orEmpty(), ReceiptLines.of(items))
    }

    private fun toReceipt(id: String, groupId: String?, data: Map<String, Any?>, venueName: String, items: List<String>) =
        PaymentReceipt(
            bookingId = id,
            recurringGroupId = groupId,
            venueName = venueName,
            items = items,
            option = PaymentOption.entries.firstOrNull { it.name == data["paymentMethod"] } ?: PaymentOption.AT_VENUE,
            status = PaymentStatus.entries.firstOrNull { it.name == data["paymentStatus"] } ?: PaymentStatus.UNPAID,
            subtotal = data.long("subtotal"),
            discount = data.long("discount"),
            total = data.long("total"),
            paidAmount = data.long("paidAmount"),
            remainingAmount = data.long("remainingAmount"),
            voucherCode = data["voucherCode"] as? String,
        )

    private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
}

/** Gộp các slot liền nhau cùng sân thành một dòng, cùng cách với `receiptLines` ở `booking-payment-confirm`. */
internal object ReceiptLines {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(zone)
    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    private data class Span(val name: String, val start: Long, var end: Long)

    fun of(items: List<Map<String, Any?>>): List<String> {
        val sorted = items.mapNotNull { item ->
            val start = millis(item["startAt"]) ?: return@mapNotNull null
            val end = millis(item["endAt"]) ?: return@mapNotNull null
            Span(item["courtName"] as? String ?: "Sân", start, end)
        }.sortedWith(compareBy({ it.name }, { it.start }))
        val spans = mutableListOf<Span>()
        for (s in sorted) {
            val last = spans.lastOrNull()
            if (last != null && last.name == s.name && last.end == s.start) last.end = s.end else spans += s.copy()
        }
        return spans.sortedBy { it.start }.map {
            val start = Instant.ofEpochMilli(it.start)
            "${it.name} · ${date.format(start)} ${time.format(start)}–${time.format(Instant.ofEpochMilli(it.end))}"
        }
    }

    private fun millis(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is java.util.Date -> value.time
        is Number -> value.toLong()
        else -> null
    }
}

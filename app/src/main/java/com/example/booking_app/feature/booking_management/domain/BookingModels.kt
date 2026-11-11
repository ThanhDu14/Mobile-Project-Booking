package com.example.booking_app.feature.booking_management.domain

/** Times are epoch milliseconds; monetary values are integer VND. */
enum class BookingTab { HOLD, PENDING, UPCOMING, COMPLETED, CANCELLED }
enum class PaymentStatus { UNPAID, DEPOSITED, PAID, REFUNDED }

data class VenueSnapshot(val name: String, val address: String, val phone: String?)
data class BookingSlotItem(
    val slotId: String, val courtId: String, val courtName: String,
    val startAtMillis: Long, val endAtMillis: Long, val price: Long
)

data class BookingFinance(
    val subtotal: Long, val discount: Long, val total: Long,
    val depositAmount: Long, val paidAmount: Long, val remainingAmount: Long,
    val paymentMethod: RefundPolicy.PaymentMethod, val paymentStatus: PaymentStatus,
    val refundAmount: Long? = null, val penaltyAmount: Long? = null
)

data class BookingDetail(
    val bookingId: String, val userId: String, val venueId: String,
    val venueSnapshot: VenueSnapshot, val items: List<BookingSlotItem>,
    val startAtMillis: Long, val endAtMillis: Long, val status: RefundPolicy.Status,
    val finance: BookingFinance, val recurringGroupId: String? = null,
    val qrToken: String? = null, val checkedInAtMillis: Long? = null,
    val holdExpiresAtMillis: Long? = null, val approvalDeadlineMillis: Long? = null,
    val cancelReason: String? = null, val cancelledBy: String? = null,
    val cancelledAtMillis: Long? = null, val completedAtMillis: Long? = null,
    val createdAtMillis: Long, val updatedAtMillis: Long
)

/** No assumptions about storage or financial allocation of recurring sessions. */
data class RecurringBookingGroup(val recurringGroupId: String, val sessions: List<BookingDetail>)

/** Local estimate only; the server recalculates at cancellation time. */
data class RefundPolicyQuote(
    val isEligibleForCancel: Boolean,
    val isEarlyCancel: Boolean,
    val minutesUntilStart: Long,
    val paidAmount: Long,
    val penaltyAmount: Long,
    val refundAmount: Long,
    val policyExplanation: String
)

data class CancelBookingRequest(val bookingId: String, val reason: String, val requestId: String)
data class CancelGroupRequest(val recurringGroupId: String, val reason: String, val requestId: String)
data class CancellationResult(val refundAmount: Long, val penaltyAmount: Long)

data class BookingPage(val bookings: List<BookingDetail>, val nextCursor: String?)

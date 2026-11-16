package com.example.booking_app.feature.payment.domain.model

/** `venues/{venueId}.payoutAccount` (data-model §1.7); chỉ có khi đủ cả 4 trường. */
data class PayoutAccount(
    val bankBin: String,
    val bankShortName: String,
    val accountNumber: String,
    val accountHolderName: String,
)

/** Phản hồi 200 của `booking-payment-transfer`: những gì màn chuyển khoản cần hiển thị. */
data class TransferInstruction(
    val bookingId: String,
    val paymentCode: String,
    val amountDue: Long,
    val holdExpiresAtMillis: Long,
    val qrUrl: String,
    val payoutAccount: PayoutAccount,
)

/** Trạng thái đơn/nhóm lấy từ listener realtime khi đang chờ chuyển khoản. */
sealed interface BookingPaymentState {
    data class AwaitingTransfer(val holdExpiresAtMillis: Long) : BookingPaymentState
    data class Confirmed(val receipt: PaymentReceipt) : BookingPaymentState
    data object Expired : BookingPaymentState
}

/** Dữ liệu hóa đơn / phiếu đặt sân. */
data class PaymentReceipt(
    val bookingId: String,
    val recurringGroupId: String?,
    val venueName: String,
    val items: List<String>,
    val option: PaymentOption,
    val status: PaymentStatus,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val paidAmount: Long,
    val remainingAmount: Long,
    val voucherCode: String?,
)

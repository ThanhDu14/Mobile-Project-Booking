package com.example.booking_app.feature.payment.domain.model

/** Lỗi từ Edge Function thanh toán, đã ánh xạ theo mã lỗi (contracts/edge-functions.md). */
sealed class PaymentError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** 409 `HOLD_EXPIRED`: slot không còn được giữ cho người này. */
    class HoldExpired : PaymentError("HOLD_EXPIRED")

    /** 409 `PRICE_CHANGED`: server tính ra khác số app gửi; kèm bảng tiền mới. */
    class PriceChanged(val breakdown: PaymentBreakdown) : PaymentError("PRICE_CHANGED")

    /** 422 `VOUCHER_INVALID` với lý do `NOT_FOUND`/`EXPIRED`/`MIN_ORDER`/`OUT_OF_USES`/`PER_USER_LIMIT`. */
    class VoucherInvalid(val reason: String) : PaymentError("VOUCHER_INVALID: $reason")

    /** 422 `TRANSFER_NOT_SUPPORTED`: cơ sở chưa có `payoutAccount`. */
    class TransferNotSupported : PaymentError("TRANSFER_NOT_SUPPORTED")

    class Network(cause: Throwable? = null) : PaymentError("NETWORK", cause)

    class Unknown(message: String? = null) : PaymentError(message)
}

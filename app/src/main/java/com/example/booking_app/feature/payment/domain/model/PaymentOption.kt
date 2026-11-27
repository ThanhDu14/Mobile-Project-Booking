package com.example.booking_app.feature.payment.domain.model

/** Cách trả người chơi chọn (data-model §1.1 `paymentMethod`). */
enum class PaymentOption { AT_VENUE, TRANSFER_DEPOSIT, TRANSFER_FULL }

/** Trạng thái thanh toán của đơn/nhóm (data-model §1.1 `paymentStatus`). */
enum class PaymentStatus { UNPAID, AWAITING_TRANSFER, DEPOSITED, PAID, REFUND_PENDING, REFUNDED }

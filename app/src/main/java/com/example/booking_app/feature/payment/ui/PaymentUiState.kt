package com.example.booking_app.feature.payment.ui

import com.example.booking_app.feature.payment.domain.model.AppliedVoucher
import com.example.booking_app.feature.payment.domain.model.PaymentBreakdown
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.Voucher

/** Tóm tắt đơn lấy từ `HoldToPaymentArgs` (FR-003). */
data class OrderSummary(
    val venueId: String,
    val venueName: String,
    val venueAddress: String,
    val isRecurring: Boolean,
    val totalSessions: Int,
    val slotCount: Int,
    /** Mỗi dòng một sân/buổi, đã định dạng sẵn ở 040/041 (`courtDetails`). */
    val items: List<String>,
    val holdExpiresAtMillis: Long,
)

/** State màn thanh toán (data-model §3). US1 dùng phần tóm tắt + voucher; các trường còn lại cho US2/US3. */
data class PaymentUiState(
    val loading: Boolean = true,
    val summary: OrderSummary? = null,
    val breakdown: PaymentBreakdown? = null,
    val voucher: AppliedVoucher? = null,
    val voucherError: String? = null,
    val applyingVoucher: Boolean = false,
    /** Danh sách cho bottom sheet chọn voucher; nạp khi mở lần đầu. */
    val availableVouchers: List<Voucher> = emptyList(),
    val vouchersLoading: Boolean = false,
    val vouchersError: String? = null,
    val transferSupported: Boolean = false,
    val selected: PaymentOption = PaymentOption.AT_VENUE,
    val holdRemainingSeconds: Long = 0,
    val submitting: Boolean = false,
    val expired: Boolean = false,
    /** Lỗi chặn cả màn (không có dữ liệu đơn). */
    val error: String? = null,
)

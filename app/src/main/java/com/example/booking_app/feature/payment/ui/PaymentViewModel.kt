package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.navigation.HoldToPaymentArgs
import com.example.booking_app.feature.payment.domain.PaymentCalculator
import com.example.booking_app.feature.payment.domain.model.AppliedVoucher
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherCheck
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.domain.usecase.PreviewVoucherUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Màn thanh toán spec 050. US1: tóm tắt đơn từ `HoldToPaymentArgs` và xem trước voucher (chỉ đọc,
 * không giữ lượt). Chốt đơn (US2), chuyển khoản (US4) và đồng hồ giữ chỗ (US3) thêm sau.
 */
@HiltViewModel
class PaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val previewVoucher: PreviewVoucherUseCase,
    private val voucherRepository: VoucherRepository,
    private val messages: PaymentMessages,
) : ViewModel() {

    private val args: HoldToPaymentArgs? = savedStateHandle[ARG_KEY]

    /**
     * Khóa idempotent gửi lên Edge Function (contracts §3): `bookingId` của args (với đơn định kỳ là
     * `recurringGroupId`); không có thì sinh một lần và giữ qua process death.
     */
    val requestId: String = args?.bookingId?.takeIf { it.isNotBlank() }
        ?: savedStateHandle.get<String>(REQUEST_ID_KEY)
        ?: UUID.randomUUID().toString().also { savedStateHandle[REQUEST_ID_KEY] = it }

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var applyJob: Job? = null

    private fun initialState(): PaymentUiState {
        val args = args ?: return PaymentUiState(loading = false, error = messages.missingOrder())
        return PaymentUiState(
            loading = false,
            summary = OrderSummary(
                venueId = args.venueId,
                venueName = args.venueName,
                venueAddress = listOf(args.venueAddress, args.districtName).filter { it.isNotBlank() }.joinToString(", "),
                isRecurring = args.isRecurring,
                totalSessions = args.totalSessions,
                slotCount = args.slotIds.size,
                items = args.courtDetails.toList(),
                holdExpiresAtMillis = args.holdExpiresAtMillis,
            ),
            breakdown = PaymentCalculator.breakdown(args.subtotalAmount, discount = 0L),
        )
    }

    /** Nhập mã rồi bấm Áp dụng. Mã sai giữ nguyên voucher đang áp (nếu có) và hiện lỗi. */
    fun applyCode(rawCode: String) {
        val args = args ?: return
        if (rawCode.isBlank()) {
            _uiState.update { it.copy(voucherError = messages.emptyCode()) }
            return
        }
        applyJob?.cancel()
        _uiState.update { it.copy(applyingVoucher = true, voucherError = null) }
        applyJob = viewModelScope.launch {
            previewVoucher(args.venueId, rawCode, args.subtotalAmount)
                .onSuccess(::onChecked)
                .onFailure { _uiState.update { it.copy(applyingVoucher = false, voucherError = messages.checkFailed()) } }
        }
    }

    /** Chọn từ danh sách: đã có voucher trong tay nên kiểm ngay, không đọc lại Firestore. */
    fun applyVoucher(voucher: Voucher) {
        val args = args ?: return
        applyJob?.cancel()
        onChecked(previewVoucher.check(voucher, args.subtotalAmount))
    }

    fun removeVoucher() {
        val args = args ?: return
        applyJob?.cancel()
        _uiState.update {
            it.copy(
                voucher = null,
                voucherError = null,
                applyingVoucher = false,
                breakdown = PaymentCalculator.breakdown(args.subtotalAmount, discount = 0L),
            )
        }
    }

    /** Nạp danh sách voucher cho bottom sheet; đã có thì thôi, lỗi thì gọi lại để thử. */
    fun loadVouchers() {
        val args = args ?: return
        val state = _uiState.value
        if (state.vouchersLoading || (state.availableVouchers.isNotEmpty() && state.vouchersError == null)) return
        viewModelScope.launch {
            _uiState.update { it.copy(vouchersLoading = true, vouchersError = null) }
            voucherRepository.available(args.venueId)
                .onSuccess { list -> _uiState.update { it.copy(vouchersLoading = false, availableVouchers = list) } }
                .onFailure { _uiState.update { it.copy(vouchersLoading = false, vouchersError = messages.listFailed()) } }
        }
    }

    fun clearVoucherError() = _uiState.update { it.copy(voucherError = null) }

    private fun onChecked(check: VoucherCheck) {
        val subtotal = args?.subtotalAmount ?: return
        when (check) {
            is VoucherCheck.Valid -> apply(check.voucher, subtotal)
            else -> _uiState.update { it.copy(applyingVoucher = false, voucherError = messages.voucherError(check)) }
        }
    }

    private fun apply(voucher: AppliedVoucher, subtotal: Long) = _uiState.update {
        it.copy(
            voucher = voucher,
            voucherError = null,
            applyingVoucher = false,
            breakdown = PaymentCalculator.breakdown(subtotal, voucher.discount),
        )
    }

    companion object {
        /** Tên argument trong `nav_payment.xml`. */
        const val ARG_KEY = "args"
        private const val REQUEST_ID_KEY = "payment_request_id"
    }
}

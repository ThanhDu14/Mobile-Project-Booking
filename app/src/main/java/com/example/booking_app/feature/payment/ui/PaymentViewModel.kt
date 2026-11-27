package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.navigation.HoldToPaymentArgs
import com.example.booking_app.feature.payment.domain.HoldClock
import com.example.booking_app.feature.payment.domain.PaymentCalculator
import com.example.booking_app.feature.payment.domain.model.AppliedVoucher
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentError
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherCheck
import com.example.booking_app.feature.payment.domain.repository.VenuePayoutRepository
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.domain.usecase.ConfirmAtVenueUseCase
import com.example.booking_app.feature.payment.domain.usecase.PreviewVoucherUseCase
import com.example.booking_app.feature.payment.domain.usecase.StartTransferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Sự kiện một lần cho `PaymentFragment` và `TransferFragment` (data-model §3). */
sealed interface PaymentEffect {
    data class OpenTransfer(val instruction: TransferInstruction, val isRecurring: Boolean) : PaymentEffect
    data class OpenReceipt(val receipt: PaymentReceipt) : PaymentEffect
    /** `retryable`: lỗi tạm thời (mất mạng), Snackbar có nút "Thử lại" gọi lại `confirm()` với cùng `requestId`. */
    data class ShowMessage(val text: String, val retryable: Boolean = false) : PaymentEffect
}

/**
 * Màn thanh toán spec 050. US1: tóm tắt đơn + xem trước voucher (chỉ đọc). US2: chọn cách trả, chốt đơn trả tại sân
 * qua `booking-payment-confirm`. US4: chuyển khoản qua `booking-payment-transfer` rồi mở màn QR; quay lại đổi sang
 * tại sân thì chốt bằng cùng `requestId` (server tắt mã cũ). US3: đồng hồ giữ chỗ [countdown].
 */
@HiltViewModel
class PaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val previewVoucher: PreviewVoucherUseCase,
    private val voucherRepository: VoucherRepository,
    private val payoutRepository: VenuePayoutRepository,
    private val confirmAtVenue: ConfirmAtVenueUseCase,
    private val startTransfer: StartTransferUseCase,
    private val clock: HoldClock,
    private val messages: PaymentMessages,
) : ViewModel() {

    private val args: HoldToPaymentArgs? = savedStateHandle[ARG_KEY]

    /**
     * Khóa idempotent gửi lên Edge Function (contracts §3): `bookingId` của args (với đơn định kỳ là
     * `recurringGroupId`); không có thì sinh một lần và giữ qua process death. Bấm lại/thử lại dùng đúng khóa này.
     */
    val requestId: String = args?.bookingId?.takeIf { it.isNotBlank() }
        ?: savedStateHandle.get<String>(REQUEST_ID_KEY)
        ?: UUID.randomUUID().toString().also { savedStateHandle[REQUEST_ID_KEY] = it }

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PaymentEffect>(Channel.BUFFERED)
    val effects: Flow<PaymentEffect> = _effects.receiveAsFlow()

    /**
     * Số giây giữ chỗ còn lại, đếm theo `holdExpiresAtMillis` mà 040/041 nhận từ server (không tạo hạn mới) và giờ
     * server (`HoldClock`). Chỉ tick khi màn đang thu (dừng khi màn ngừng hiển thị); về 0 thì khóa nút và báo hết hạn.
     */
    val countdown: StateFlow<Long> = flow {
        val expiresAt = args?.holdExpiresAtMillis ?: return@flow
        while (true) {
            val remainingMs = expiresAt - clock.nowMillis()
            val seconds = if (remainingMs <= 0) 0 else (remainingMs + 999) / 1000
            emit(seconds)
            if (seconds == 0L) return@flow
            delay(remainingMs - (seconds - 1) * 1000)
        }
    }
        .onEach(::onTick)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), _uiState.value.holdRemainingSeconds)

    private var applyJob: Job? = null
    private var confirmed = false

    init {
        args?.let { loadPayout(it.venueId) }
    }

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
                items = OrderItems.from(args),
                holdExpiresAtMillis = args.holdExpiresAtMillis,
            ),
            breakdown = PaymentCalculator.breakdown(args.subtotalAmount, discount = 0L),
        )
    }

    /** Có `payoutAccount` mới cho chuyển khoản; đọc lỗi thì coi như không hỗ trợ (vẫn trả tại sân được). */
    private fun loadPayout(venueId: String) = viewModelScope.launch {
        val supported = payoutRepository.payoutAccount(venueId).getOrNull() != null
        _uiState.update { it.copy(transferSupported = supported) }
    }

    // ---------- Voucher (US1) ----------

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
        applyJob?.cancel()
        clearVoucher(error = null)
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

    private fun clearVoucher(error: String?) {
        val subtotal = args?.subtotalAmount ?: return
        _uiState.update {
            it.copy(
                voucher = null,
                voucherError = error,
                applyingVoucher = false,
                breakdown = PaymentCalculator.breakdown(subtotal, discount = 0L),
            )
        }
    }

    // ---------- Cách trả và chốt đơn (US2) ----------

    /** Chuyển khoản chỉ chọn được khi cơ sở có tài khoản nhận tiền. */
    fun select(option: PaymentOption) {
        if (option != PaymentOption.AT_VENUE && !_uiState.value.transferSupported) return
        _uiState.update { it.copy(selected = option) }
    }

    /**
     * Bấm nút xác nhận. Chỉ một lời gọi chạy cùng lúc (bấm liên tục không gọi thêm), đã chốt hoặc đã hết hạn thì
     * bỏ qua. Tổng 0đ luôn chốt như trả tại sân (server ghi `PAID`). Chuyển khoản thì mở màn QR; người chơi quay lại
     * vẫn bấm được (gọi lại nhận cùng mã) hoặc đổi sang trả tại sân với cùng `requestId`.
     */
    fun confirm() {
        val args = args ?: return
        val state = _uiState.value
        val breakdown = state.breakdown ?: return
        if (state.submitting || state.expired || confirmed || state.applyingVoucher) return
        val option = if (breakdown.isFree) PaymentOption.AT_VENUE else state.selected
        if (option != state.selected) _uiState.update { it.copy(selected = option) }
        val request = ConfirmRequest(
            requestId = requestId,
            recurringGroupId = if (args.isRecurring) requestId else null,
            venueId = args.venueId,
            slotIds = args.slotIds.toList(),
            voucherCode = state.voucher?.code,
            expectedTotal = breakdown.total,
        )
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            if (option == PaymentOption.AT_VENUE) {
                val result = confirmAtVenue(request)
                val receipt = result.getOrNull()
                if (receipt != null) {
                    confirmed = true
                    _uiState.update { it.copy(submitting = false) }
                    _effects.send(PaymentEffect.OpenReceipt(receipt))
                } else {
                    onConfirmFailed(result.exceptionOrNull() ?: PaymentError.Unknown())
                }
            } else {
                val result = startTransfer(request, option)
                val instruction = result.getOrNull()
                if (instruction != null) {
                    _uiState.update { it.copy(submitting = false) }
                    _effects.send(PaymentEffect.OpenTransfer(instruction, args.isRecurring))
                } else {
                    onConfirmFailed(result.exceptionOrNull() ?: PaymentError.Unknown())
                }
            }
        }
    }

    private fun onTick(seconds: Long) {
        _uiState.update { it.copy(holdRemainingSeconds = seconds) }
        if (seconds == 0L && !confirmed && !_uiState.value.expired) {
            _uiState.update { it.copy(expired = true) }
            _effects.trySend(PaymentEffect.ShowMessage(messages.string(PaymentMessages.Key.HOLD_EXPIRED)))
        }
    }

    private suspend fun onConfirmFailed(error: Throwable) {
        _uiState.update { it.copy(submitting = false) }
        val message = when (error) {
            is PaymentError.HoldExpired -> {
                _uiState.update { it.copy(expired = true) }
                messages.string(PaymentMessages.Key.HOLD_EXPIRED)
            }
            is PaymentError.PriceChanged -> {
                _uiState.update { it.copy(breakdown = error.breakdown) }
                messages.string(PaymentMessages.Key.PRICE_CHANGED)
            }
            is PaymentError.VoucherInvalid -> {
                val text = messages.voucherError(error.toCheck())
                clearVoucher(error = text)
                text
            }
            is PaymentError.NoPrice -> messages.string(PaymentMessages.Key.NO_PRICE)
            is PaymentError.TransferNotSupported -> {
                _uiState.update { it.copy(transferSupported = false, selected = PaymentOption.AT_VENUE) }
                messages.string(PaymentMessages.Key.TRANSFER_UNSUPPORTED)
            }
            is PaymentError.Network -> messages.string(PaymentMessages.Key.CONFIRM_NETWORK)
            else -> messages.string(PaymentMessages.Key.CONFIRM_FAILED)
        }
        if (message != null) _effects.send(PaymentEffect.ShowMessage(message, retryable = error is PaymentError.Network))
    }

    private fun PaymentError.VoucherInvalid.toCheck(): VoucherCheck = when (reason) {
        "EXPIRED" -> VoucherCheck.Expired
        "MIN_ORDER" -> VoucherCheck.MinOrderNotMet(minOrderTotal)
        "OUT_OF_USES" -> VoucherCheck.OutOfUses
        "PER_USER_LIMIT" -> VoucherCheck.PerUserLimitReached
        else -> VoucherCheck.NotFound
    }

    companion object {
        /** Tên argument trong `nav_payment.xml`. */
        const val ARG_KEY = "args"
        private const val REQUEST_ID_KEY = "payment_request_id"
    }
}

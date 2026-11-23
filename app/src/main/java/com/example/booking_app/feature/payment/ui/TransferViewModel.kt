package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.payment.domain.HoldClock
import com.example.booking_app.feature.payment.domain.model.BookingPaymentState
import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import com.example.booking_app.feature.payment.domain.usecase.ObserveBookingPaymentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferUiState(
    val instruction: TransferInstruction? = null,
    val holdRemainingSeconds: Long = 0,
    val expired: Boolean = false,
)

/**
 * Màn chuyển khoản VietQR (spec 050 US4, T047). Hiện mã QR/thông tin tài khoản từ args (phản hồi
 * `booking-payment-transfer`), đếm ngược theo `holdExpiresAt` của server, và lắng nghe đơn: webhook SePay chốt thì
 * mở hóa đơn. Đồng hồ về 0 vẫn tiếp tục nghe, vì server cho ân hạn tới khi `expire-holds` dọn đơn (research R4).
 */
@HiltViewModel
class TransferViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observePayment: ObserveBookingPaymentUseCase,
    private val clock: HoldClock,
    private val messages: PaymentMessages,
) : ViewModel() {

    val bookingId: String = savedStateHandle["bookingId"] ?: ""
    val isRecurring: Boolean = savedStateHandle["isRecurring"] ?: false

    private val _uiState = MutableStateFlow(
        TransferUiState(
            instruction = TransferInstruction(
                bookingId = bookingId,
                paymentCode = savedStateHandle["paymentCode"] ?: "",
                amountDue = savedStateHandle["amountDue"] ?: 0L,
                holdExpiresAtMillis = savedStateHandle["holdExpiresAtMillis"] ?: 0L,
                qrUrl = savedStateHandle["qrUrl"] ?: "",
                payoutAccount = PayoutAccount(
                    bankBin = "",
                    bankShortName = savedStateHandle["bankShortName"] ?: "",
                    accountNumber = savedStateHandle["accountNumber"] ?: "",
                    accountHolderName = savedStateHandle["accountHolderName"] ?: "",
                ),
                recurringGroupId = if (isRecurring) bookingId else null,
            ),
        ),
    )
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PaymentEffect>(Channel.BUFFERED)
    val effects: Flow<PaymentEffect> = _effects.receiveAsFlow()

    private var ticker: Job? = null
    private var done = false

    init {
        startTicker()
        viewModelScope.launch {
            observePayment(bookingId, isRecurring).collect(::onState)
        }
    }

    fun copiedMessage(): String = messages.string(PaymentMessages.Key.COPIED)

    private suspend fun onState(state: BookingPaymentState) {
        if (done) return
        when (state) {
            is BookingPaymentState.Confirmed -> {
                done = true
                ticker?.cancel()
                _effects.send(PaymentEffect.OpenReceipt(state.receipt))
            }
            BookingPaymentState.Expired -> {
                ticker?.cancel()
                markExpired()
            }
            is BookingPaymentState.AwaitingTransfer -> {
                val current = _uiState.value.instruction ?: return
                if (current.holdExpiresAtMillis != state.holdExpiresAtMillis) {
                    _uiState.update { it.copy(instruction = current.copy(holdExpiresAtMillis = state.holdExpiresAtMillis)) }
                    if (!_uiState.value.expired) startTicker()
                }
            }
        }
    }

    /** Tick mỗi giây theo giờ server; không tạo hạn mới ở app (FR-016). */
    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val expiresAt = _uiState.value.instruction?.holdExpiresAtMillis ?: return@launch
                val remainingMs = expiresAt - clock.nowMillis()
                val seconds = if (remainingMs <= 0) 0 else (remainingMs + 999) / 1000
                _uiState.update { it.copy(holdRemainingSeconds = seconds) }
                if (seconds == 0L) {
                    markExpired()
                    return@launch
                }
                delay(remainingMs - (seconds - 1) * 1000)
            }
        }
    }

    private fun markExpired() {
        if (_uiState.value.expired) return
        _uiState.update { it.copy(expired = true, holdRemainingSeconds = 0) }
        _effects.trySend(PaymentEffect.ShowMessage(messages.string(PaymentMessages.Key.HOLD_EXPIRED)))
    }
}

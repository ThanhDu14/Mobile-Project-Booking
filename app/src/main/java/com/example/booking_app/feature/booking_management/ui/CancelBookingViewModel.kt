package com.example.booking_app.feature.booking_management.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking_management.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CancelBookingUiState(
    val booking: BookingDetail? = null,
    val quote: RefundPolicyQuote? = null,
    val selectedReasonKey: String = "",
    val otherReasonText: String = "",
    val isConfirmEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class CancelBookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookingManagementRepository,
    private val calculateQuote: CalculateRefundQuoteUseCase,
    private val cancelBookingUseCase: CancelBookingUseCase,
    private val serverTimeOffsetProvider: ServerTimeOffsetProvider
) : ViewModel() {

    private val bookingId: String = savedStateHandle["bookingId"] ?: ""

    private val _uiState = MutableStateFlow(CancelBookingUiState(isLoading = true))
    val uiState: StateFlow<CancelBookingUiState> = _uiState.asStateFlow()

    private var activeRequestId: String = UUID.randomUUID().toString()

    init {
        if (bookingId.isNotBlank()) {
            loadBooking(bookingId)
        } else {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Thiếu mã đơn đặt sân") }
        }
    }

    private fun loadBooking(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.observeBooking(id).collect { booking ->
                    if (booking == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Không tìm thấy đơn đặt sân") }
                    } else {
                        val now = serverTimeOffsetProvider.now()
                        val quote = calculateQuote(
                            status = booking.status,
                            paymentMethod = booking.finance.paymentMethod,
                            paidAmount = booking.finance.paidAmount,
                            serverNowEstimateMillis = now,
                            startMillis = booking.startAtMillis,
                            checkedIn = booking.checkedInAtMillis != null
                        )
                        _uiState.update {
                            it.copy(
                                booking = booking,
                                quote = quote,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun onReasonSelected(reasonKey: String) {
        _uiState.update {
            val enabled = isReasonValid(reasonKey, it.otherReasonText)
            it.copy(selectedReasonKey = reasonKey, isConfirmEnabled = enabled)
        }
    }

    fun onOtherReasonTextChanged(text: String) {
        _uiState.update {
            val enabled = isReasonValid(it.selectedReasonKey, text)
            it.copy(otherReasonText = text, isConfirmEnabled = enabled)
        }
    }

    private fun isReasonValid(key: String, otherText: String): Boolean {
        if (key.isBlank()) return false
        if (key == "OTHER") return otherText.trim().isNotBlank()
        return true
    }

    fun confirmCancel() {
        val current = _uiState.value
        val booking = current.booking ?: return
        val reasonText = if (current.selectedReasonKey == "OTHER") {
            current.otherReasonText.trim()
        } else {
            current.selectedReasonKey
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val request = CancelBookingRequest(
                    bookingId = booking.bookingId,
                    reason = reasonText,
                    requestId = activeRequestId
                )
                val result = cancelBookingUseCase(request)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSuccess = true,
                        resultMessage = "Hủy đơn thành công. Đã hoàn ${result.refundAmount} đ vào ví giả lập."
                    )
                }
            } catch (e: UnsupportedOperationException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Hệ thống đang bảo trì giao dịch hoàn tiền phía máy chủ. Vui lòng liên hệ hỗ trợ hoặc thử lại sau."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Không thể hủy đơn đặt sân"
                    )
                }
            }
        }
    }
}

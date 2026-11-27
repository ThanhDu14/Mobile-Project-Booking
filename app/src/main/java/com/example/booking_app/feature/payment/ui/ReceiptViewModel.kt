package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReceiptUiState {
    data object Loading : ReceiptUiState
    data object Error : ReceiptUiState
    data class Content(val receipt: PaymentReceipt) : ReceiptUiState
}

/** Đọc lại đơn/nhóm đã chốt để hiện hóa đơn (T033); args `bookingId`, `isRecurring` của `nav_payment`. */
@HiltViewModel
class ReceiptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PaymentRepository,
) : ViewModel() {

    val bookingId: String = savedStateHandle["bookingId"] ?: ""
    val isRecurring: Boolean = savedStateHandle["isRecurring"] ?: false

    private val _uiState = MutableStateFlow<ReceiptUiState>(ReceiptUiState.Loading)
    val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = ReceiptUiState.Loading
        viewModelScope.launch {
            _uiState.value = repository.receipt(bookingId, isRecurring)
                .fold({ ReceiptUiState.Content(it) }, { ReceiptUiState.Error })
        }
    }
}

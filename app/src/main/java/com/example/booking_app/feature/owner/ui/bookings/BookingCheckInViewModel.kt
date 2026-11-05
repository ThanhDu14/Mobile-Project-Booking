package com.example.booking_app.feature.owner.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInInput
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BookingCheckInUiState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val result: String? = null,
)

@HiltViewModel
class BookingCheckInViewModel @Inject constructor(
    private val repository: BookingCheckInRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingCheckInUiState())
    val uiState = _uiState.asStateFlow()

    fun submit(code: String, paymentCollected: Boolean) {
        val input = BookingCheckInInput(code, paymentCollected).normalized()
        if (input.credential.isBlank()) {
            _uiState.value = BookingCheckInUiState(error = "Nhập mã đặt sân hoặc quét mã QR.")
            return
        }
        viewModelScope.launch {
            _uiState.value = BookingCheckInUiState(isSubmitting = true)
            repository.checkIn(input)
                .onSuccess { checked -> _uiState.value = BookingCheckInUiState(result = checked.bookingId) }
                .onFailure { failure -> _uiState.value = BookingCheckInUiState(error = failure.message ?: "Không thể check-in") }
        }
    }
}

package com.example.booking_app.feature.booking_management.ui

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.BookingManagementRepository
import com.example.booking_app.feature.booking_management.domain.GenerateQrCodeUseCase
import com.example.booking_app.feature.booking_management.domain.RefundPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BookingDetailState {
    data object Loading : BookingDetailState
    data class Content(val booking: BookingDetail, val qrBitmap: Bitmap?) : BookingDetailState
    data object NotFound : BookingDetailState
    data class Error(val message: String) : BookingDetailState
}

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookingManagementRepository,
    private val generateQrCode: GenerateQrCodeUseCase
) : ViewModel() {

    private val bookingId: String = savedStateHandle["bookingId"] ?: ""

    private val _state = MutableStateFlow<BookingDetailState>(BookingDetailState.Loading)
    val state: StateFlow<BookingDetailState> = _state.asStateFlow()

    init {
        if (bookingId.isNotBlank()) {
            loadBooking(bookingId)
        } else {
            _state.value = BookingDetailState.NotFound
        }
    }

    fun loadBooking(id: String) {
        viewModelScope.launch {
            _state.value = BookingDetailState.Loading
            try {
                repository.observeBooking(id).collect { detail ->
                    if (detail == null) {
                        _state.value = BookingDetailState.NotFound
                    } else {
                        val qrBitmap = if (detail.status == RefundPolicy.Status.CONFIRMED &&
                            detail.checkedInAtMillis == null &&
                            !detail.qrToken.isNullOrBlank()
                        ) {
                            try {
                                val matrix = generateQrCode(detail.qrToken)
                                QrBitmapRenderer.render(matrix)
                            } catch (_: Exception) {
                                null
                            }
                        } else null

                        _state.value = BookingDetailState.Content(detail, qrBitmap)
                    }
                }
            } catch (e: Exception) {
                _state.value = BookingDetailState.Error(e.message ?: "Không thể tải chi tiết đơn")
            }
        }
    }
}

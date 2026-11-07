package com.example.booking_app.feature.owner.ui.vouchers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucher
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherDraft
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherRepository
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherValidation
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OwnerVoucherUiState(
    val venues: List<OwnerVenue> = emptyList(),
    val selectedVenueId: String? = null,
    val vouchers: List<OwnerVoucher> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
    val validationErrors: List<String> = emptyList(),
)

@HiltViewModel
class OwnerVoucherViewModel @Inject constructor(
    private val venueRepository: OwnerVenueRepository,
    private val voucherRepository: OwnerVoucherRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerVoucherUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var venueJob: Job? = null

    init { loadVenues() }

    fun refresh() = _uiState.value.selectedVenueId?.let(::selectVenue) ?: loadVenues()

    fun selectVenue(venueId: String) {
        if (_uiState.value.selectedVenueId != venueId) _uiState.value = _uiState.value.copy(selectedVenueId = venueId)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, vouchers = emptyList())
            try {
                voucherRepository.observeVouchers(venueId).collect { vouchers ->
                    _uiState.value = _uiState.value.copy(vouchers = vouchers, isLoading = false, error = null, isEmpty = vouchers.isEmpty())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = failure.message ?: "Không tải được voucher", isEmpty = false)
            }
        }
    }

    fun create(venueId: String, draft: OwnerVoucherDraft) {
        val errors = OwnerVoucherValidation.validate(draft)
        if (errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(validationErrors = errors, error = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, validationErrors = emptyList())
            voucherRepository.create(venueId, draft)
                .onSuccess { _uiState.value = _uiState.value.copy(isSaving = false) }
                .onFailure { failure -> _uiState.value = _uiState.value.copy(isSaving = false, error = failure.message ?: "Không tạo được voucher") }
        }
    }

    fun setActive(venueId: String, code: String, isActive: Boolean) {
        viewModelScope.launch {
            voucherRepository.setActive(venueId, code, isActive)
                .onFailure { failure -> _uiState.value = _uiState.value.copy(error = failure.message ?: "Không cập nhật được trạng thái voucher") }
        }
    }

    private fun loadVenues() {
        venueJob?.cancel()
        venueJob = viewModelScope.launch {
            venueRepository.getOwnedVenues()
                .onSuccess { venues ->
                    _uiState.value = _uiState.value.copy(venues = venues, isLoading = false, error = null)
                    venues.firstOrNull()?.id?.let(::selectVenue)
                }
                .onFailure { failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = failure.message ?: "Không tải được cơ sở") }
        }
    }
}

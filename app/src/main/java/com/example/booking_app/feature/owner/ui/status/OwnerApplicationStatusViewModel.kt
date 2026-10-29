package com.example.booking_app.feature.owner.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusPresentation
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMapping
import com.example.booking_app.feature.owner.domain.usecase.GetOwnerApplicationStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface OwnerApplicationStatusUiState {
    data object Loading : OwnerApplicationStatusUiState
    data object Empty : OwnerApplicationStatusUiState
    data class Content(val value: OwnerApplicationStatusPresentation) : OwnerApplicationStatusUiState
    data class Error(val message: String) : OwnerApplicationStatusUiState
}

/** Loads only server-authoritative status; failures never grant owner access from cached data. */
@HiltViewModel
class OwnerApplicationStatusViewModel @Inject constructor(
    private val getStatus: GetOwnerApplicationStatusUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<OwnerApplicationStatusUiState>(OwnerApplicationStatusUiState.Loading)
    val uiState: StateFlow<OwnerApplicationStatusUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    fun beginRefresh() {
        refreshJob?.cancel()
        _uiState.value = OwnerApplicationStatusUiState.Loading
        refreshJob = viewModelScope.launch {
            try {
                val result = getStatus()
                _uiState.value = when (result) {
                    is OwnerApplicationStatusMapping.Content -> OwnerApplicationStatusUiState.Content(result.value)
                    OwnerApplicationStatusMapping.Empty -> OwnerApplicationStatusUiState.Empty
                    OwnerApplicationStatusMapping.Ambiguous ->
                        OwnerApplicationStatusUiState.Error("Trạng thái hồ sơ chưa xác định. Vui lòng tải lại.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = OwnerApplicationStatusUiState.Error("Không thể tải trạng thái. Vui lòng thử lại.")
            }
        }
    }
}

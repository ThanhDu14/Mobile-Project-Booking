package com.example.booking_app.feature.account.ui.district

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.model.District
import com.example.booking_app.feature.account.domain.validation.DistrictSelectionRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DistrictPickerUiState {
    data object Loading : DistrictPickerUiState

    data object Empty : DistrictPickerUiState

    data object Error : DistrictPickerUiState

    data class Content(val districts: List<District>, val selectedIds: List<String>) : DistrictPickerUiState {
        val canSelectMore: Boolean get() = DistrictSelectionRule.canAdd(selectedIds)
    }
}

/**
 * Chọn khu vực hay chơi (FR-002). Lựa chọn ban đầu lấy từ Safe Args `selectedIds` và sống qua xoay màn hình.
 * Khu vực đã ngừng vẫn nằm trong lựa chọn (không có trong danh sách nên không bỏ chọn ở đây được, bỏ bằng chip).
 */
@HiltViewModel
class DistrictPickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val districtRepository: DistrictRepository,
) : ViewModel() {

    private val districts = MutableStateFlow<Result<List<District>>?>(null)
    private val selected = savedStateHandle.getStateFlow(
        KEY_SELECTED,
        savedStateHandle.get<Array<String>>(ARG_SELECTED_IDS).orEmpty().toList().let(::ArrayList),
    )

    val uiState: StateFlow<DistrictPickerUiState> = combine(districts, selected) { result, ids ->
        when {
            result == null -> DistrictPickerUiState.Loading
            result.isFailure -> DistrictPickerUiState.Error
            result.getOrThrow().isEmpty() -> DistrictPickerUiState.Empty
            else -> DistrictPickerUiState.Content(result.getOrThrow(), ids)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DistrictPickerUiState.Loading)

    init {
        load()
    }

    fun retry() = load()

    fun toggle(id: String) {
        val ids = selected.value
        val next = when {
            id in ids -> ids - id
            DistrictSelectionRule.canAdd(ids) -> ids + id
            else -> return
        }
        savedStateHandle[KEY_SELECTED] = ArrayList(next)
    }

    fun selectedIds(): List<String> = selected.value

    private fun load() {
        districts.value = null
        viewModelScope.launch {
            districts.value = try {
                Result.success(districtRepository.activeDistricts())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private companion object {
        const val ARG_SELECTED_IDS = "selectedIds"
        const val KEY_SELECTED = "picker_selected"
    }
}

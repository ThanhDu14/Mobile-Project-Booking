package com.example.booking_app.feature.owner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.owner.domain.OwnerErrorClassifier
import com.example.booking_app.feature.owner.domain.OwnerErrorKind
import com.example.booking_app.feature.owner.domain.OwnerValidation
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.PriceRuleCoverage
import com.example.booking_app.feature.owner.domain.PriceRuleInput
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerPriceRuleUiState(
    val isLoading: Boolean = false,
    val venue: OwnerVenue? = null,
    val rules: List<OwnerPriceRule> = emptyList(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val errorKind: OwnerErrorKind? = null,
    val message: String? = null,
    val isFresh: Boolean = false,
    val gapWarningRule: OwnerPriceRule? = null
) {
    val isEmpty get() = !isLoading && error == null && rules.isEmpty()
}

@HiltViewModel
class OwnerPriceRuleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OwnerVenueRepository
) : ViewModel() {
    val venueId: String = checkNotNull(savedStateHandle["venueId"])
    private val _uiState = MutableStateFlow(OwnerPriceRuleUiState())
    val uiState: StateFlow<OwnerPriceRuleUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, isFresh = false)
        val venueResult = repository.getVenue(venueId)
        val rulesResult = repository.getPriceRules(venueId)
        if (venueResult.isSuccess && rulesResult.isSuccess) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                venue = venueResult.getOrThrow(),
                rules = rulesResult.getOrThrow().sortedWith(compareBy({ it.daysOfWeek.firstOrNull() ?: 0 }, { it.startTime })),
                error = null,
                errorKind = null,
                isFresh = true
            )
        } else {
            val failure = venueResult.exceptionOrNull() ?: rulesResult.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = failure?.message ?: "Không tải được bảng giá",
                errorKind = failure?.let(OwnerErrorClassifier::kind),
                isFresh = false
            )
        }
    }

    fun save(editingRuleId: String?, ruleInput: PriceRuleInput) = viewModelScope.launch {
        if (_uiState.value.isSubmitting) return@launch
        val otherRules = _uiState.value.rules
            .filterNot { it.id == editingRuleId }
            .map { PriceRuleInput(it.daysOfWeek, it.startTime, it.endTime, it.pricePerHour, it.label) }

        val errors = OwnerValidation.priceRule(ruleInput, otherRules)
        if (errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(message = errors.first().message)
            return@launch
        }

        _uiState.value = _uiState.value.copy(isSubmitting = true, message = null)
        val actionResult = if (editingRuleId == null) {
            repository.createPriceRule(venueId, ruleInput).map { Unit }
        } else {
            repository.updatePriceRule(venueId, editingRuleId, ruleInput)
        }

        actionResult.onSuccess {
            _uiState.value = _uiState.value.copy(isSubmitting = false, message = "Đã lưu bảng giá")
            load()
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                isSubmitting = false,
                message = error.message ?: "Không thể lưu bảng giá",
                errorKind = OwnerErrorClassifier.kind(error)
            )
            if (OwnerErrorClassifier.kind(error) == OwnerErrorKind.CONFLICT) load()
        }
    }

    fun requestDelete(rule: OwnerPriceRule) {
        val venue = _uiState.value.venue
        val openTime = venue?.openTime ?: "0600"
        val closeTime = venue?.closeTime ?: "2200"

        val leavesGap = rule.daysOfWeek.any { day ->
            PriceRuleCoverage.deletionLeavesCoverageGap(rule.id, day, openTime, closeTime, _uiState.value.rules)
        }

        if (leavesGap) {
            _uiState.value = _uiState.value.copy(gapWarningRule = rule)
        } else {
            confirmDelete(rule.id)
        }
    }

    fun dismissGapWarning() {
        _uiState.value = _uiState.value.copy(gapWarningRule = null)
    }

    fun confirmDelete(ruleId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(gapWarningRule = null)
        if (_uiState.value.isSubmitting) return@launch
        _uiState.value = _uiState.value.copy(isSubmitting = true, message = null)

        repository.deletePriceRule(venueId, ruleId).onSuccess {
            _uiState.value = _uiState.value.copy(isSubmitting = false, message = "Đã xóa khung giá")
            load()
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                isSubmitting = false,
                message = error.message ?: "Không thể xóa khung giá",
                errorKind = OwnerErrorClassifier.kind(error)
            )
            if (OwnerErrorClassifier.kind(error) == OwnerErrorKind.CONFLICT) load()
        }
    }
}

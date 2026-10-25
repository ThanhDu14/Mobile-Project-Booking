package com.example.booking_app.feature.account.ui.district

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeDistrictRepository
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.District
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DistrictPickerViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeDistrictRepository()

    private fun viewModel(selected: Array<String> = arrayOf("q-cu", "q3")) =
        DistrictPickerViewModel(SavedStateHandle(mapOf("selectedIds" to selected)), repository)

    @Test
    fun `dang tai roi co du lieu, giu ca khu vuc da ngung trong lua chon`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        assertEquals(DistrictPickerUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        val content = vm.uiState.value as DistrictPickerUiState.Content
        assertEquals(listOf("q1", "q3", "q10"), content.districts.map { it.id })
        assertEquals(listOf("q-cu", "q3"), content.selectedIds)
    }

    @Test
    fun `chon toi da 5, du 5 thi khong chon them duoc nhung van bo chon duoc`() = runTest(mainRule.dispatcher) {
        repository.active = (1..7).map { District("d$it", "D$it", it) }
        val vm = viewModel(arrayOf("d1", "d2", "d3", "d4"))
        advanceUntilIdle()
        vm.toggle("d5")
        vm.toggle("d6")
        advanceUntilIdle()
        val content = vm.uiState.value as DistrictPickerUiState.Content
        assertEquals(listOf("d1", "d2", "d3", "d4", "d5"), content.selectedIds)
        assertFalse(content.canSelectMore)
        vm.toggle("d1")
        advanceUntilIdle()
        assertTrue((vm.uiState.value as DistrictPickerUiState.Content).canSelectMore)
    }

    @Test
    fun `danh muc rong thi Empty`() = runTest(mainRule.dispatcher) {
        repository.active = emptyList()
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(DistrictPickerUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `loi thi Error, Thu lai thi tai lai`() = runTest(mainRule.dispatcher) {
        repository.error = AccountError.Network
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(DistrictPickerUiState.Error, vm.uiState.value)
        repository.error = null
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DistrictPickerUiState.Content)
    }
}

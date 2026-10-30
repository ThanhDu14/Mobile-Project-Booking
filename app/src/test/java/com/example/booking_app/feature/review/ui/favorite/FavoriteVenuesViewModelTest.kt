package com.example.booking_app.feature.review.ui.favorite

import app.cash.turbine.test
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.feature.review.FakeFavoriteRepository
import com.example.booking_app.feature.review.domain.FavoriteRepository
import com.example.booking_app.feature.review.domain.model.FavoriteVenue.Availability
import com.example.booking_app.feature.review.domain.usecase.ToggleFavoriteUseCase
import com.example.booking_app.feature.review.favoriteVenue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteVenuesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeFavoriteRepository()

    private fun viewModel() = FavoriteVenuesViewModel(repository, ToggleFavoriteUseCase(repository))

    @Test
    fun `danh sach rong - Empty`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(UiState.Empty, vm.uiState.value)
    }

    @Test
    fun `co du lieu - Content giu thu tu va trang thai tam ngung, khong ton tai`() = runTest {
        repository.favorites.value = listOf(
            favoriteVenue("a"),
            favoriteVenue("b", Availability.SUSPENDED),
            favoriteVenue("c", Availability.GONE),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val content = vm.uiState.value as UiState.Content
        assertEquals(listOf("a", "b", "c"), content.data.map { it.venueId })
        assertEquals(
            listOf(Availability.ACTIVE, Availability.SUSPENDED, Availability.GONE),
            content.data.map { it.availability },
        )
    }

    @Test
    fun `loi tai - Error, thu lai thanh cong thi co du lieu`() = runTest {
        repository.loadError = AppError.Network
        repository.favorites.value = listOf(favoriteVenue("a"))
        val vm = viewModel()
        advanceUntilIdle()
        val error = vm.uiState.value as UiState.Error

        repository.loadError = null
        error.retry!!.invoke()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is UiState.Content)
    }

    @Test
    fun `bo yeu thich - xoa khoi danh sach`() = runTest {
        repository.favorites.value = listOf(favoriteVenue("a"), favoriteVenue("b"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.unfavorite(favoriteVenue("a"))
        advanceUntilIdle()

        val content = vm.uiState.value as UiState.Content
        assertEquals(listOf("b"), content.data.map { it.venueId })
        assertEquals(listOf("a" to false), repository.setCalls)
    }

    @Test
    fun `bo san cuoi cung - Empty`() = runTest {
        repository.favorites.value = listOf(favoriteVenue("a"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.unfavorite(favoriteVenue("a"))
        advanceUntilIdle()

        assertEquals(UiState.Empty, vm.uiState.value)
    }

    @Test
    fun `bo yeu thich loi - san quay lai danh sach va bao snackbar`() = runTest {
        repository.favorites.value = listOf(favoriteVenue("a"), favoriteVenue("b"))
        repository.setResult = Result.failure(AppError.Unknown(null))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.unfavorite(favoriteVenue("a"))
            assertEquals(FavoriteListEvent.SyncFailed, awaitItem())
        }
        val content = vm.uiState.value as UiState.Content
        assertEquals(listOf("a", "b"), content.data.map { it.venueId })
    }

    @Test
    fun `cuon cuoi danh sach day trang - tai them trang sau`() = runTest {
        repository.favorites.value = (1..FavoriteRepository.PAGE_SIZE + 5).map { favoriteVenue("v$it") }
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(20, 40), repository.requestedLimits)
        assertEquals(25, (vm.uiState.value as UiState.Content).data.size)
    }

    @Test
    fun `trang chua day - khong tai them`() = runTest {
        repository.favorites.value = listOf(favoriteVenue("a"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(20), repository.requestedLimits)
    }
}

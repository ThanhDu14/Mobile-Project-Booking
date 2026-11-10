package com.example.booking_app.feature.search.ui

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.search.domain.HomeVenueRepository
import com.example.booking_app.feature.search.domain.model.HomeSort
import com.example.booking_app.feature.search.domain.model.HomeVenue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Spec 020 US5, FR-016, FR-019, FR-029. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private class FakeRepo(var result: () -> List<HomeVenue>) : HomeVenueRepository {
        var calls = 0
        override suspend fun activeVenues(): List<HomeVenue> {
            calls++
            return result()
        }
    }

    private fun venues(n: Int) = (1..n).map {
        HomeVenue("v$it", "Sân $it", null, null, minPricePerHour = (100 - it) * 1_000L, ratingAvg = it / 10.0, ratingCount = it)
    }

    @Test
    fun `tai xong hien 20 san dau, tong N, sap theo danh gia`() = runTest {
        val vm = HomeViewModel(FakeRepo { venues(25) })
        advanceUntilIdle()
        val state = vm.uiState.value as HomeUiState.Content
        assertEquals(25, state.total)
        assertEquals(20, state.items.size)
        assertEquals("v25", state.items.first().id)
        assertEquals(HomeSort.RATING, state.sort)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `cuon toi cuoi thi tai them trang tiep`() = runTest {
        val vm = HomeViewModel(FakeRepo { venues(25) })
        advanceUntilIdle()
        vm.onLoadMore()
        val state = vm.uiState.value as HomeUiState.Content
        assertEquals(25, state.items.size)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `doi cach sap xep thi ve trang dau va khong doc lai`() = runTest {
        val repo = FakeRepo { venues(25) }
        val vm = HomeViewModel(repo)
        advanceUntilIdle()
        vm.onLoadMore()
        vm.onSortSelected(HomeSort.PRICE)
        val state = vm.uiState.value as HomeUiState.Content
        assertEquals(HomeSort.PRICE, state.sort)
        assertEquals(20, state.items.size)
        assertEquals("v25", state.items.first().id) // giá thấp nhất
        assertEquals(1, repo.calls)
    }

    @Test
    fun `khong co san thi Empty`() = runTest {
        val vm = HomeViewModel(FakeRepo { emptyList() })
        advanceUntilIdle()
        assertEquals(HomeUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `loi thi Error, thu lai thanh cong thi Content`() = runTest {
        var fail = true
        val repo = FakeRepo { if (fail) throw AppError.Network else venues(3) }
        val vm = HomeViewModel(repo)
        advanceUntilIdle()
        assertEquals(HomeUiState.Error(AppError.Network), vm.uiState.value)
        fail = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(3, (vm.uiState.value as HomeUiState.Content).total)
    }
}

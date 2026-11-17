package com.example.booking_app.feature.search.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.location.LocationProvider
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.search.domain.AiSearchUsage
import com.example.booking_app.feature.search.domain.CourtAiApi
import com.example.booking_app.feature.search.domain.CourtSearchCriteria
import com.example.booking_app.feature.search.domain.CourtSearchPage
import com.example.booking_app.feature.search.domain.CourtSearchRepository
import com.example.booking_app.feature.search.domain.MemoryAiQueryHistory
import com.example.booking_app.feature.search.domain.RelaxationKind
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Hai sân: A ở Quận 10 giá 120k, B ở Quận 1 giá 80k. Tìm "Quận 10 dưới 100k" ra 0 sân.
 * Repository giả tính giá biến thể từ chính danh sách này, đếm số lần được hỏi.
 */
private class TwoVenueRepository : CourtSearchRepository {
    private val venues = listOf(
        HomeVenue("a", "A", "Quận 10", null, 120_000, 0.0, 0) to "q10",
        HomeVenue("b", "B", "Quận 1", null, 80_000, 0.0, 0) to "q1",
    )
    var variantCalls = 0

    private fun matching(criteria: CourtSearchCriteria) = venues
        .filter { (_, district) -> criteria.districtId == null || criteria.districtId == district }
        .map { it.first }.filter { criteria.acceptsPrice(it.minPricePerHour) }

    override fun search(criteria: CourtSearchCriteria, origin: GeoPoint?) =
        flowOf(CourtSearchPage(matching(criteria), complete = true, capped = false))

    override suspend fun variantPrices(variants: List<CourtSearchCriteria>): List<List<Long?>> {
        variantCalls++
        return variants.map { variant -> matching(variant).map { it.minPricePerHour } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RelaxationViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = mockk<CourtAiApi>().also { coEvery { it.usage() } returns AiSearchUsage(0, 0, 10) }
    private val history = FakeSearchHistory()

    private fun viewModel(repo: CourtSearchRepository): CourtSearchViewModel {
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns listOf(District("q10", "Quận 10", 1, true), District("q1", "Quận 1", 2, true))
        coEvery { catalog.amenities() } returns emptyList()
        return CourtSearchViewModel(SavedStateHandle(), repo, api, catalog, mockk<LocationProvider>(),
            FakeSessionManager(SessionState.SignedIn("player", AppRole.PLAYER, true)),
            FakeClock { Instant.parse("2026-11-15T03:00:00Z").toEpochMilli() },
            MemoryAiQueryHistory(), history, FakeVoiceInput())
    }

    @Test
    fun emptyFilteredSearchSuggestsRelaxationsWithoutAi() = runTest {
        val repo = TwoVenueRepository()
        val vm = viewModel(repo)
        runCurrent()
        vm.applyFilters("q10", null, 100_000, emptySet(), null)
        runCurrent()

        val relaxations = vm.state.value.relaxations
        assertTrue(vm.state.value.venues.isEmpty())
        // Giá gần nhất có sân ở Quận 10 là 120k; bỏ giới hạn quận thì có B (80k).
        assertEquals(listOf(RelaxationKind.PRICE, RelaxationKind.ALL_DISTRICTS), relaxations.map { it.kind })
        assertEquals(120_000L, relaxations[0].criteria.priceMax)
        assertEquals(listOf(1, 1), relaxations.map { it.count })
        coVerify(exactly = 0) { api.parse(any()) }

        // Bấm gợi ý: bộ lọc đổi theo, danh sách có sân, gợi ý biến mất, lần tìm được lưu lịch sử.
        vm.applyRelaxation(relaxations[0])
        runCurrent()
        assertEquals(120_000L, vm.state.value.criteria.priceMax)
        assertEquals("q10", vm.state.value.criteria.districtId)
        assertEquals(listOf("a"), vm.state.value.venues.map { it.id })
        assertTrue(vm.state.value.relaxations.isEmpty())
        assertEquals(120_000L, history.recorded.last().first.priceMax)
    }

    @Test
    fun noSuggestionsWithoutFiltersOrWhenResultsExist() = runTest {
        val repo = TwoVenueRepository()
        val vm = viewModel(repo)
        runCurrent()
        // Danh sách mặc định (không lọc) có sân: không gợi ý.
        assertTrue(vm.state.value.relaxations.isEmpty())
        // Có lọc và có kết quả: không gợi ý.
        vm.district("q1")
        runCurrent()
        assertEquals(listOf("b"), vm.state.value.venues.map { it.id })
        assertTrue(vm.state.value.relaxations.isEmpty())
        assertEquals(0, repo.variantCalls)
    }

    @Test
    fun noWorkingRelaxationKeepsThePlainEmptyState() = runTest {
        val repo = object : CourtSearchRepository {
            override fun search(criteria: CourtSearchCriteria, origin: GeoPoint?) =
                flowOf(CourtSearchPage(emptyList(), complete = true, capped = false))
            // Không cài variantPrices: mặc định mọi biến thể đều 0 sân.
        }
        val vm = viewModel(repo)
        runCurrent()
        vm.applyFilters("q10", null, 100_000, emptySet(), null)
        runCurrent()
        assertTrue(vm.state.value.venues.isEmpty())
        assertTrue(vm.state.value.relaxations.isEmpty())
    }
}

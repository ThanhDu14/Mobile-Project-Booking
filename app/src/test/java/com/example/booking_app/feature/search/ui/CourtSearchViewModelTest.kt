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
import com.example.booking_app.feature.search.domain.AiCourtFilter
import com.example.booking_app.feature.search.domain.AiCourtParse
import com.example.booking_app.feature.search.domain.AiParseResponse
import com.example.booking_app.feature.search.domain.AiSearchUsage
import com.example.booking_app.feature.search.domain.CourtAiApi
import com.example.booking_app.feature.search.domain.CourtSearchPage
import com.example.booking_app.feature.search.domain.CourtSearchRepository
import com.example.booking_app.feature.search.domain.MemoryAiQueryHistory
import com.example.booking_app.feature.search.domain.ReviewPreference
import com.example.booking_app.feature.search.domain.ReviewRecommendation
import com.example.booking_app.feature.search.domain.model.HomeVenue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CourtSearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun loadsActiveVenuesWithoutFiltersAndReloadsAfterFilterIsRemoved() = runTest {
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns emptyList()
        coEvery { catalog.amenities() } returns emptyList()
        val seen = mutableListOf<com.example.booking_app.feature.search.domain.CourtSearchCriteria>()
        val repo = object : CourtSearchRepository {
            override fun search(criteria: com.example.booking_app.feature.search.domain.CourtSearchCriteria,
                origin: com.google.firebase.firestore.GeoPoint?) = flowOf(
                CourtSearchPage(listOf(HomeVenue("a", "A", null, null, 100_000, 0.0, 0)), true, false)
            ).also { seen += criteria }
        }
        val vm = CourtSearchViewModel(SavedStateHandle(), repo, mockk<CourtAiApi>(), catalog,
            mockk<LocationProvider>(), FakeSessionManager(SessionState.SignedOut(null)),
            FakeClock { Instant.parse("2026-11-12T03:00:00Z").toEpochMilli() }, MemoryAiQueryHistory(), FakeSearchHistory())
        runCurrent()
        assertEquals(listOf("a"), vm.state.value.venues.map { it.id })
        assertTrue(!seen.last().hasFilters())
        vm.price(null, 100_000)
        runCurrent()
        vm.price(null, null)
        runCurrent()
        assertTrue(!seen.last().hasFilters())
        assertEquals(listOf("a"), vm.state.value.venues.map { it.id })
    }

    @Test
    fun mainResultsDisplayWhileRecommendationsArePendingAndLateResultIsDiscarded() = runTest {
        val review = CompletableDeferred<List<ReviewRecommendation>>()
        val api = mockk<CourtAiApi>()
        val usage = AiSearchUsage(1, 1, 10)
        coEvery { api.usage() } returns usage
        coEvery { api.parse(any()) } returns AiParseResponse(
            AiCourtParse("search", filter = AiCourtFilter(districtId = "q10"), preference = "GRIP"), usage,
        )
        coEvery { api.recommend(ReviewPreference.GRIP, listOf("a")) } coAnswers { review.await() }
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns listOf(District("q10", "Quận 10", 1, true))
        coEvery { catalog.amenities() } returns emptyList()
        val session = FakeSessionManager(SessionState.SignedIn("player", AppRole.PLAYER, true))
        val repo = object : CourtSearchRepository {
            override fun search(
                criteria: com.example.booking_app.feature.search.domain.CourtSearchCriteria,
                origin: com.google.firebase.firestore.GeoPoint?,
            ) = flowOf(CourtSearchPage(
                if (criteria.priceMax == null) listOf(HomeVenue("a", "A", null, null, 100_000, 0.0, 0)) else emptyList(),
                complete = true, capped = false,
            ))
        }
        val history = MemoryAiQueryHistory()
        val vm = CourtSearchViewModel(SavedStateHandle(), repo, api, catalog, mockk<LocationProvider>(), session,
            FakeClock { Instant.parse("2026-11-12T03:00:00Z").toEpochMilli() }, history, FakeSearchHistory())
        runCurrent()
        vm.input("Quận 10, sàn ít trơn")
        vm.submitAi()
        runCurrent()
        assertEquals(listOf("Quận 10, sàn ít trơn"), vm.state.value.aiHistory)
        assertEquals(listOf("a"), vm.state.value.venues.map { it.id })
        assertTrue(vm.state.value.recommendations.isEmpty())
        vm.price(null, 50_000)
        runCurrent()
        review.complete(listOf(ReviewRecommendation("a", emptyList())))
        runCurrent()
        assertTrue(vm.state.value.venues.isEmpty())
        assertTrue(vm.state.value.recommendations.isEmpty())
    }

    @Test
    fun pastDateAsksForDateAgainWithNotice() = runTest {
        val api = mockk<CourtAiApi>()
        val usage = AiSearchUsage(1, 1, 10)
        coEvery { api.usage() } returns usage
        coEvery { api.parse(any()) } returns AiParseResponse(
            AiCourtParse("unclear", filter = AiCourtFilter(districtId = "q10", date = null), dropped = listOf("INVALID_DATE")),
            usage,
        )
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns listOf(District("q10", "Quận 10", 1, true))
        coEvery { catalog.amenities() } returns emptyList()
        val repo = object : CourtSearchRepository {
            override fun search(
                criteria: com.example.booking_app.feature.search.domain.CourtSearchCriteria,
                origin: com.google.firebase.firestore.GeoPoint?,
            ) = flowOf(CourtSearchPage(emptyList(), complete = true, capped = false))
        }
        val session = FakeSessionManager(SessionState.SignedIn("player", AppRole.PLAYER, true))
        val vm = CourtSearchViewModel(SavedStateHandle(), repo, api, catalog, mockk<LocationProvider>(), session,
            FakeClock { Instant.parse("2026-11-15T03:00:00Z").toEpochMilli() }, MemoryAiQueryHistory(), FakeSearchHistory())
        runCurrent()
        vm.input("Hôm qua 7h tối Quận 10")
        vm.submitAi()
        runCurrent()
        assertEquals(SearchNotice.INVALID_DATE, vm.state.value.notice)
        assertTrue(vm.state.value.clarification != null)
    }

    @Test
    fun clearingAiTextKeepsFiltersAndOffersToClearThem() = runTest {
        val api = mockk<CourtAiApi>()
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns listOf(District("q10", "Quận 10", 1, true))
        coEvery { catalog.amenities() } returns emptyList()
        val repo = object : CourtSearchRepository {
            override fun search(
                criteria: com.example.booking_app.feature.search.domain.CourtSearchCriteria,
                origin: com.google.firebase.firestore.GeoPoint?,
            ) = flowOf(CourtSearchPage(emptyList(), complete = true, capped = false))
        }
        val session = FakeSessionManager(SessionState.SignedIn("player", AppRole.PLAYER, true))
        val vm = CourtSearchViewModel(SavedStateHandle(), repo, api, catalog, mockk<LocationProvider>(), session,
            FakeClock { Instant.parse("2026-11-15T03:00:00Z").toEpochMilli() }, MemoryAiQueryHistory(), FakeSearchHistory())
        runCurrent()
        vm.district("q10")
        runCurrent()
        vm.input("abc")
        assertEquals(false, vm.state.value.filtersKept)
        vm.input("")
        assertEquals(true, vm.state.value.filtersKept)
        assertEquals("q10", vm.state.value.criteria.districtId)
        // "Xóa bộ lọc" có sẵn giữ lại quận và từ khóa; gợi ý chỉ được tắt.
        vm.clearFilters()
        assertEquals(false, vm.state.value.filtersKept)
        assertEquals("q10", vm.state.value.criteria.districtId)
    }

    @Test
    fun aiHistoryMasksContactsDedupesAndKeepsFiveNewest() {
        val history = MemoryAiQueryHistory()
        history.add("u", "Gọi 0901 234 567 hoặc a.b@mail.com tối mai")
        assertEquals(listOf("Gọi [phone] hoặc [email] tối mai"), history.read("u"))
        (1..6).forEach { history.add("u", "câu $it") }
        history.add("u", "CÂU 3")
        assertEquals(listOf("CÂU 3", "câu 6", "câu 5", "câu 4", "câu 2"), history.read("u"))
        assertTrue(history.read("other").isEmpty())
        history.remove("u", "câu 6")
        assertEquals(4, history.read("u").size)
    }
}

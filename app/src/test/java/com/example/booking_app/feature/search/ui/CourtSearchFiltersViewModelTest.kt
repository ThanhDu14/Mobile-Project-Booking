package com.example.booking_app.feature.search.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.location.LocationProvider
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.search.domain.CourtAiApi
import com.example.booking_app.feature.search.domain.CourtSearchCriteria
import com.example.booking_app.feature.search.domain.CourtSearchPage
import com.example.booking_app.feature.search.domain.CourtSearchRepository
import com.example.booking_app.feature.search.domain.CourtSearchSort
import com.example.booking_app.feature.search.domain.MemoryAiQueryHistory
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Bộ lọc, sắp xếp và gõ từ khóa trên màn tìm sân (spec 020 T043, T060, T072). */
@OptIn(ExperimentalCoroutinesApi::class)
class CourtSearchFiltersViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** Repository giả ghi lại tiêu chí và vị trí của mỗi lần tìm. */
    private class RecordingRepository : CourtSearchRepository {
        val searches = mutableListOf<Pair<CourtSearchCriteria, GeoPoint?>>()
        override fun search(criteria: CourtSearchCriteria, origin: GeoPoint?) =
            flowOf(CourtSearchPage(emptyList(), complete = true, capped = false)).also { searches += criteria to origin }
    }

    private val location = mockk<LocationProvider>()

    private fun viewModel(repo: CourtSearchRepository): CourtSearchViewModel {
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns emptyList()
        coEvery { catalog.amenities() } returns emptyList()
        return CourtSearchViewModel(SavedStateHandle(), repo, mockk<CourtAiApi>(), catalog, location,
            FakeSessionManager(SessionState.SignedOut(null)),
            FakeClock { Instant.parse("2026-11-18T03:00:00Z").toEpochMilli() },
            MemoryAiQueryHistory(), FakeSearchHistory(), FakeVoiceInput())
    }

    @Test
    fun typingQuicklySearchesOnceWithTheLastKeyword() = runTest {
        val repo = RecordingRepository()
        val vm = viewModel(repo)
        runCurrent()
        val before = repo.searches.size

        vm.keyword("mi")
        advanceTimeBy(100)
        vm.keyword("min")
        advanceTimeBy(100)
        vm.keyword("minh")
        advanceTimeBy(299)
        runCurrent()
        assertEquals("chưa đủ 300 ms thì chưa tìm", before, repo.searches.size)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("minh"), repo.searches.drop(before).map { it.first.keyword })
    }

    @Test
    fun removingOneFilterKeepsTheOthersAndClearKeepsKeywordDistrictAndSort() = runTest {
        val repo = RecordingRepository()
        val vm = viewModel(repo)
        runCurrent()
        vm.applyFilters("q10", 50_000, 100_000, setOf("parking"), null)
        vm.sort(CourtSearchSort.PRICE_ASC)
        vm.keyword("minh")
        advanceTimeBy(301)
        runCurrent()

        // Bỏ chip giá: quận, tiện ích, từ khóa, sắp xếp giữ nguyên.
        vm.price(null, null)
        runCurrent()
        val afterRemove = vm.state.value.criteria
        assertEquals(CourtSearchCriteria("minh", "q10", null, null, setOf("parking"), null, CourtSearchSort.PRICE_ASC), afterRemove)

        // "Xóa bộ lọc" giữ từ khóa, quận và sắp xếp, bỏ phần còn lại (US2).
        vm.amenities(setOf("parking", "shower"))
        vm.price(null, 80_000)
        vm.clearFilters()
        runCurrent()
        assertEquals(CourtSearchCriteria(keyword = "minh", districtId = "q10", sort = CourtSearchSort.PRICE_ASC),
            vm.state.value.criteria)
        assertEquals(vm.state.value.criteria, repo.searches.last().first)
    }

    @Test
    fun nearestWithoutPermissionAsksForLocationAndKeepsTheCurrentSort() = runTest {
        every { location.hasPermission() } returns false
        val repo = RecordingRepository()
        val vm = viewModel(repo)
        runCurrent()
        val before = repo.searches.size

        vm.sort(CourtSearchSort.NEAREST)
        runCurrent()
        assertEquals(CourtSearchEffect.RequestLocation, vm.effects.first())
        assertEquals(CourtSearchSort.RATING, vm.state.value.criteria.sort)
        assertEquals(before, repo.searches.size)
    }

    @Test
    fun nearestUsesTheDeviceLocationOrExplainsWhenItIsUnavailable() = runTest {
        every { location.hasPermission() } returns true
        val here = GeoPoint(10.8128, 106.6283)
        coEvery { location.current() } returns here
        val repo = RecordingRepository()
        val vm = viewModel(repo)
        runCurrent()

        vm.sort(CourtSearchSort.NEAREST)
        runCurrent()
        assertEquals(CourtSearchSort.NEAREST, repo.searches.last().first.sort)
        assertEquals(here, repo.searches.last().second)
        assertNull(vm.state.value.notice)

        // Mất vị trí (tắt GPS, quá 10 giây): báo cho người dùng, không đổi cách sắp xếp.
        coEvery { location.current() } returns null
        val other = viewModel(RecordingRepository())
        runCurrent()
        other.sort(CourtSearchSort.NEAREST)
        runCurrent()
        assertEquals(SearchNotice.LOCATION_UNAVAILABLE, other.state.value.notice)
        assertEquals(CourtSearchSort.RATING, other.state.value.criteria.sort)
    }
}

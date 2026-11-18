package com.example.booking_app.feature.search.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.location.LocationProvider
import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.session.AppRole
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
import com.example.booking_app.feature.search.domain.SearchHistory
import com.example.booking_app.feature.search.domain.SearchHistoryEntry
import com.example.booking_app.feature.search.domain.SearchHistoryRepository
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Repository giả: ghi lại các lần lưu/xóa, danh sách quan sát được do test đặt. */
internal class FakeSearchHistory(initial: List<SearchHistoryEntry> = emptyList()) : SearchHistoryRepository {
    val entries = MutableStateFlow(initial)
    val recorded = mutableListOf<Pair<CourtSearchCriteria, Boolean>>()
    val deleted = mutableListOf<String>()

    override fun observe(uid: String) = entries

    override suspend fun record(uid: String, criteria: CourtSearchCriteria, prune: Boolean) {
        recorded += criteria to prune
    }

    override suspend fun delete(uid: String, id: String) {
        deleted += id
    }

    override suspend fun clear(uid: String, ids: List<String>) {
        deleted += ids
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    // 2026-11-15 10:00 giờ Việt Nam.
    private val clock = FakeClock { Instant.parse("2026-11-15T03:00:00Z").toEpochMilli() }

    private fun viewModel(
        history: FakeSearchHistory,
        signedIn: Boolean = true,
        voice: FakeVoiceInput = FakeVoiceInput(),
    ): CourtSearchViewModel {
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns listOf(District("q10", "Quận 10", 1, true))
        coEvery { catalog.amenities() } returns emptyList()
        val repo = object : CourtSearchRepository {
            override fun search(criteria: CourtSearchCriteria, origin: GeoPoint?) =
                flowOf(CourtSearchPage(emptyList(), complete = true, capped = false))
        }
        val session = FakeSessionManager(
            if (signedIn) SessionState.SignedIn("player", AppRole.PLAYER, true) else SessionState.SignedOut(null),
        )
        return CourtSearchViewModel(SavedStateHandle(), repo, mockk<CourtAiApi>(), catalog,
            mockk<LocationProvider>(), session, clock, MemoryAiQueryHistory(), history, voice)
    }

    @Test
    fun savesSearchesButNotKeystrokesOrSortOnly() = runTest {
        val history = FakeSearchHistory()
        val vm = viewModel(history)
        runCurrent()

        vm.sort(CourtSearchSort.PRICE_ASC) // sắp xếp riêng không phải bộ lọc
        vm.keyword("Trường Chinh")         // gõ từng ký tự: chưa lưu
        runCurrent()
        assertTrue(history.recorded.isEmpty())

        vm.submitKeyword()                 // bấm tìm trên bàn phím
        vm.district("q10")                 // chọn quận
        runCurrent()
        assertEquals(
            listOf("Trường Chinh" to null, "Trường Chinh" to "q10"),
            history.recorded.map { (criteria, _) -> criteria.keyword to criteria.districtId },
        )
        assertTrue(history.recorded.none { (_, prune) -> prune })
    }

    @Test
    fun oddPricesAreRoundedSoTheSearchCanBeSaved() = runTest {
        val history = FakeSearchHistory()
        val vm = viewModel(history)
        runCurrent()

        vm.price(40_500, 55_500)
        runCurrent()
        // Rules chỉ nhận bội số 1.000: trước đây lần ghi này bị từ chối mà không báo.
        assertEquals(40_000L to 56_000L, vm.state.value.criteria.let { it.priceMin to it.priceMax })
        assertEquals(listOf(40_000L to 56_000L), history.recorded.map { (c, _) -> c.priceMin to c.priceMax })
    }

    @Test
    fun guestSearchesAreNeverSaved() = runTest {
        val history = FakeSearchHistory()
        val vm = viewModel(history, signedIn = false)
        runCurrent()
        vm.district("q10")
        vm.recordSearch()
        runCurrent()
        assertTrue(history.recorded.isEmpty())
        assertTrue(vm.state.value.history.isEmpty())
    }

    @Test
    fun restoringAnEntryDropsAPastWindowAndMovesItToTheTop() = runTest {
        val past = BookingSearchWindow(LocalDate.of(2026, 11, 14), TimeOfDay(19 * 60), TimeOfDay(21 * 60))
        val saved = CourtSearchCriteria(keyword = "Trường Chinh", districtId = "q10", priceMax = 100_000, window = past)
        val history = FakeSearchHistory(listOf(SearchHistoryEntry(SearchHistory.idOf(saved), saved)))
        val vm = viewModel(history)
        runCurrent()
        assertEquals(1, vm.state.value.history.size)

        vm.applyHistory(vm.state.value.history.first())
        runCurrent()
        val criteria = vm.state.value.criteria
        assertEquals("Trường Chinh", criteria.keyword)
        assertEquals("q10", criteria.districtId)
        assertEquals(100_000L, criteria.priceMax)
        assertNull(criteria.window)
        assertEquals(SearchNotice.HISTORY_DATE_PAST, vm.state.value.notice)
        assertEquals(criteria, history.recorded.single().first)
    }

    @Test
    fun aFullListIsPrunedOnlyForANewCombination() = runTest {
        val entries = (1..SearchHistory.LIMIT).map { index ->
            val criteria = CourtSearchCriteria(keyword = "sân $index")
            SearchHistoryEntry(SearchHistory.idOf(criteria), criteria)
        }
        val history = FakeSearchHistory(entries)
        val vm = viewModel(history)
        runCurrent()

        vm.applyHistory(entries.first()) // tổ hợp đã có: chỉ đưa lên đầu
        vm.district("q10")               // tổ hợp mới khi đã đủ 10 mục: dọn mục cũ
        runCurrent()
        assertEquals(listOf(false, true), history.recorded.map { (_, prune) -> prune })
    }

    @Test
    fun deleteAndClearUpdateTheListRightAway() = runTest {
        val first = CourtSearchCriteria(districtId = "q10")
        val second = CourtSearchCriteria(keyword = "Minh Tâm")
        val history = FakeSearchHistory(
            listOf(SearchHistoryEntry("a", first), SearchHistoryEntry("b", second)),
        )
        val vm = viewModel(history)
        runCurrent()
        vm.deleteHistory("a")
        assertEquals(listOf("b"), vm.state.value.history.map { it.id })
        vm.clearHistory()
        runCurrent()
        assertTrue(vm.state.value.history.isEmpty())
        assertEquals(listOf("a", "b"), history.deleted)
    }

    @Test
    fun sameCombinationGivesTheSameId() {
        val base = CourtSearchCriteria(keyword = "Trường Chinh", districtId = "q10", amenityIds = setOf("parking", "drinks"))
        assertEquals(
            SearchHistory.idOf(base),
            SearchHistory.idOf(base.copy(keyword = "  truong CHINH ", amenityIds = setOf("drinks", "parking"))),
        )
        assertNotEquals(SearchHistory.idOf(base), SearchHistory.idOf(base.copy(districtId = "q1")))
        assertEquals(20, SearchHistory.idOf(base).length)
    }
}

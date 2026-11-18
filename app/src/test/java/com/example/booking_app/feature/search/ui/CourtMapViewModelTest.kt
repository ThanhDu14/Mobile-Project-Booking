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
import com.example.booking_app.feature.search.domain.MemoryAiQueryHistory
import com.example.booking_app.feature.search.domain.map.CourtMap
import com.example.booking_app.feature.search.domain.map.MapViewport
import com.example.booking_app.feature.search.domain.model.LatLng
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Chế độ bản đồ trên màn tìm sân (spec 021 FR-001, FR-004, FR-005, FR-009 → FR-013). */
@OptIn(ExperimentalCoroutinesApi::class)
class CourtMapViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class RecordingRepository : CourtSearchRepository {
        val searches = mutableListOf<Pair<CourtSearchCriteria, GeoPoint?>>()
        override fun search(criteria: CourtSearchCriteria, origin: GeoPoint?) =
            flowOf(CourtSearchPage(emptyList(), complete = true, capped = false)).also { searches += criteria to origin }
    }

    private val location = mockk<LocationProvider>()
    private val repo = RecordingRepository()
    private val viewport = MapViewport(10.83, 10.79, 106.67, 106.60, 14.0)

    private fun viewModel(): CourtSearchViewModel {
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns emptyList()
        coEvery { catalog.amenities() } returns emptyList()
        return CourtSearchViewModel(SavedStateHandle(), repo, mockk<CourtAiApi>(), catalog, location,
            FakeSessionManager(SessionState.SignedOut(null)),
            FakeClock { Instant.parse("2026-11-18T03:00:00Z").toEpochMilli() },
            MemoryAiQueryHistory(), FakeSearchHistory(), FakeVoiceInput())
    }

    @Test
    fun switchingModesKeepsTheFiltersAndTheViewport() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.applyFilters("tan-phu", null, 40_000, emptySet(), null)
        vm.showMap(true)
        vm.mapMoved(viewport, byUser = false)
        vm.selectVenue("909")
        val searches = repo.searches.size

        vm.showMap(false)
        assertEquals("tan-phu", vm.state.value.criteria.districtId)
        assertEquals(40_000L, vm.state.value.criteria.priceMax)
        assertEquals("đổi chế độ không tìm lại", searches, repo.searches.size)
        // Về danh sách thì bỏ chọn; vùng xem giữ để mở lại bản đồ đúng chỗ (FR-009).
        assertNull(vm.map.value.selectedId)
        vm.showMap(true)
        assertEquals(viewport, vm.map.value.viewport)
    }

    @Test
    fun draggingMarksTheAreaAsMovedWithoutSearchingUntilFiltersChange() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.showMap(true)
        vm.mapMoved(viewport, byUser = false)
        assertFalse(vm.map.value.moved)
        val searches = repo.searches.size

        vm.mapMoved(viewport.copy(zoom = 15.0), byUser = true)
        assertTrue(vm.map.value.moved)
        assertEquals("kéo/phóng không tự tải lại (FR-004)", searches, repo.searches.size)

        vm.price(null, 100_000)
        runCurrent()
        assertFalse(vm.map.value.moved)
    }

    @Test
    fun searchThisAreaDropsTheDistrictFilterWithANotice() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.applyFilters("tan-phu", null, 40_000, emptySet(), null)
        vm.showMap(true)
        vm.mapMoved(viewport, byUser = true)

        vm.searchThisArea()
        runCurrent()
        assertNull(vm.state.value.criteria.districtId)
        assertEquals(40_000L, vm.state.value.criteria.priceMax)
        assertEquals(SearchNotice.MAP_DISTRICT_DROPPED, vm.state.value.notice)
        assertFalse(vm.map.value.moved)
        assertNull(repo.searches.last().first.districtId)

        // Không có lọc quận: kết quả đã đủ, chỉ ẩn nút.
        val searches = repo.searches.size
        vm.mapMoved(viewport, byUser = true)
        vm.searchThisArea()
        assertFalse(vm.map.value.moved)
        assertEquals(searches, repo.searches.size)
    }

    @Test
    fun myLocationAsksForPermissionOnlyWhenTapped() = runTest {
        every { location.hasPermission() } returns false
        val vm = viewModel()
        runCurrent()
        vm.showMap(true)
        vm.locateOnMap()
        assertEquals(CourtSearchEffect.RequestLocation(LocationReason.MAP), vm.effects.first())

        // Từ chối: báo không có vị trí, bản đồ giữ khu vực mặc định (FR-013).
        vm.locationResult(LocationReason.MAP, granted = false)
        assertEquals(SearchNotice.LOCATION_UNAVAILABLE, vm.state.value.notice)
        assertNull(vm.map.value.myLocation)
        assertNull(vm.map.value.focus)
    }

    @Test
    fun myLocationCentersTheMapAndComputesDistancesOnTheDevice() = runTest {
        every { location.hasPermission() } returns true
        val here = GeoPoint(10.8128, 106.6283)
        coEvery { location.current() } returns here
        val vm = viewModel()
        runCurrent()
        vm.showMap(true)

        vm.locateOnMap()
        runCurrent()
        assertEquals(LatLng(10.8128, 106.6283), vm.map.value.myLocation)
        val focus = vm.map.value.focus!!
        assertEquals(CourtMap.FOCUS_ZOOM, focus.zoom, 0.0)
        // Tìm lại với vị trí để thẻ có khoảng cách; vị trí không nằm trong tiêu chí/lịch sử (FR-015).
        assertEquals(here, repo.searches.last().second)
        assertEquals(CourtSearchCriteria(), repo.searches.last().first)

        // Bấm lại: lệnh mới để bản đồ quay về vị trí dù người dùng đã kéo đi.
        vm.locateOnMap()
        runCurrent()
        assertTrue(vm.map.value.focus!!.id > focus.id)
    }

    @Test
    fun tappingAMarkerAnchorsTheCardsAndSwipingOnlyMovesTheHighlight() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.showMap(true)

        vm.selectVenue("909")
        assertEquals("909", vm.map.value.selectedId)
        assertEquals("909", vm.map.value.cardAnchorId)

        // Vuốt sang thẻ kế bên: thứ tự thẻ giữ theo sân đã chạm (FR-008).
        vm.selectVenue("k3", fromCard = true)
        assertEquals("k3", vm.map.value.selectedId)
        assertEquals("909", vm.map.value.cardAnchorId)

        // Chạm vùng trống: ẩn thẻ, bỏ nổi bật (FR-010).
        vm.selectVenue(null)
        assertNull(vm.map.value.selectedId)
        assertNull(vm.map.value.cardAnchorId)
    }

    @Test
    fun nearbyNeverPromptsOnItsOwnAndUsesAnExistingPermission() = runTest {
        every { location.hasPermission() } returns false
        val vm = viewModel()
        runCurrent()
        vm.startNearby()
        assertEquals(NearbyStatus.NEED_LOCATION, vm.state.value.nearbyStatus)

        // Bấm "Bật vị trí": lúc này mới xin quyền (FR-012).
        vm.enableNearby()
        assertEquals(CourtSearchEffect.RequestLocation(LocationReason.NEARBY), vm.effects.first())

        // Quyền đã có từ trước: lấy vị trí ngay khi màn hình hiện.
        every { location.hasPermission() } returns true
        coEvery { location.current() } returns GeoPoint(10.8128, 106.6283)
        val granted = viewModel()
        runCurrent()
        granted.startNearby()
        runCurrent()
        assertEquals(NearbyStatus.READY, granted.state.value.nearbyStatus)
        assertEquals(LatLng(10.8128, 106.6283), granted.state.value.nearbyLocation)
        assertEquals(LatLng(10.8128, 106.6283), granted.map.value.myLocation)
        // Vị trí không đi vào tiêu chí tìm kiếm (FR-015).
        assertEquals(CourtSearchCriteria(), granted.state.value.criteria)
    }

    @Test
    fun nearbyReportsAMissingPositionAndOpensTheMapAroundTheUser() = runTest {
        every { location.hasPermission() } returns true
        coEvery { location.current() } returns null
        val vm = viewModel()
        runCurrent()
        vm.startNearby()
        runCurrent()
        assertEquals(NearbyStatus.UNAVAILABLE, vm.state.value.nearbyStatus)
        vm.openNearbyMap(13.5)
        assertFalse("chưa có vị trí thì không mở bản đồ", vm.map.value.visible)

        coEvery { location.current() } returns GeoPoint(10.8128, 106.6283)
        vm.enableNearby()
        runCurrent()
        vm.openNearbyMap(13.5)
        assertTrue(vm.map.value.visible)
        assertEquals(LatLng(10.8128, 106.6283), vm.map.value.focus!!.center)
        assertEquals(13.5, vm.map.value.focus!!.zoom, 0.0)
    }
}

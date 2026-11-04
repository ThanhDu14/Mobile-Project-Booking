package com.example.booking_app.feature.courtdetail.ui

import app.cash.turbine.test
import com.example.booking_app.core.catalog.Amenity
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.courtdetail.domain.model.Court
import com.example.booking_app.feature.courtdetail.domain.model.OpeningStatus
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import com.example.booking_app.feature.courtdetail.domain.model.SurfaceType
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.example.booking_app.feature.courtdetail.testVenue
import com.example.booking_app.feature.courtdetail.vnMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class CourtDetailViewModelUs1Test {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `header hien khi venue toi, khong cho courts va priceRules`() = runTest {
        val h = CourtDetailHarness(this)
        val gate = CompletableDeferred<Unit>()
        h.repository.courts = { gate.await(); listOf(Court("c1", SurfaceType.PU, true)) }
        h.repository.priceRules = { gate.await(); emptyList() }
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        runCurrent()
        assertNull(vm.uiState.value.availability)
        assertEquals(Section.Loading, vm.uiState.value.header)

        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        val state = vm.uiState.value
        assertEquals(VenueAvailability.ACTIVE, state.availability)
        assertTrue(state.header is Section.Content)
        assertEquals(Section.Loading, state.courts)

        gate.complete(Unit)
        runCurrent()
        val courts = vm.uiState.value.courts as Section.Content
        assertEquals(1, courts.data.activeCount)
    }

    @Test
    fun `courts loi chi muc do loi, thu lai chi goi lai loadCourts`() = runTest {
        val h = CourtDetailHarness(this)
        h.repository.courts = { throw AppError.Network }
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        assertEquals(Section.Error(AppError.Network), vm.uiState.value.courts)
        assertTrue(vm.uiState.value.header is Section.Content)

        val priceCallsBefore = h.repository.loadPriceRulesCalls
        h.repository.courts = { listOf(Court("c1", SurfaceType.WOOD, true)) }
        vm.onRetry(SectionId.COURTS)
        runCurrent()
        assertTrue(vm.uiState.value.courts is Section.Content)
        assertEquals(2, h.repository.loadCourtsCalls)
        assertEquals(priceCallsBefore, h.repository.loadPriceRulesCalls)
    }

    @Test
    fun `khong co tien ich thi Empty, co thi Content`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue(amenities = emptyList())))
        runCurrent()
        assertEquals(Section.Empty, vm.uiState.value.amenities)

        val parking = Amenity("parking", "Gửi xe", "parking", 1, true)
        h.repository.emit(VenueResult.Found(testVenue(amenities = listOf(parking))))
        runCurrent()
        assertEquals(Section.Content(listOf(parking)), vm.uiState.value.amenities)
    }

    @Test
    fun `du lieu tu cache thi bao offline`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue(isFromCache = true)))
        runCurrent()
        assertTrue(vm.uiState.value.isOffline)
    }

    @Test
    fun `dat san mang theo khung gio sau khi kiem tra server`() = runTest {
        val h = CourtDetailHarness(this, date = "20261027", start = "1900", end = "2100")
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        vm.effects.test {
            vm.onBookClicked()
            runCurrent()
            assertEquals(
                CourtDetailEffect.OpenBookingGrid(
                    "demo-truong-chinh-909",
                    SearchWindow(LocalDate.of(2026, 10, 27), TimeOfDay.parse("1900")!!, TimeOfDay.parse("2100")!!),
                ),
                awaitItem(),
            )
        }
        assertEquals(1, h.repository.fetchStatusCalls)
    }

    @Test
    fun `khung gio da qua thi dat san khong mang khung gio`() = runTest {
        val h = CourtDetailHarness(this, date = "20261026", start = "1900", end = "2100")
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        assertNull(vm.uiState.value.window)
        vm.effects.test {
            vm.onBookClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.OpenBookingGrid("demo-truong-chinh-909", null), awaitItem())
        }
    }

    @Test
    fun `luu vi tri anh vao SavedStateHandle`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        vm.onPhotoPageChanged(2)
        runCurrent()
        assertEquals(2, h.handle.get<Int>("photoIndex"))
        assertEquals(2, vm.uiState.value.photoIndex)
    }

    @Test
    fun `cham anh mo man hinh xem anh`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        vm.effects.test {
            vm.onPhotoClicked(1)
            assertEquals(CourtDetailEffect.OpenPhotoViewer(testVenue().photoUrls, 1), awaitItem())
        }
    }

    @Test
    fun `trang thai mo cua cap nhat khi qua 23h`() = runTest {
        val h = CourtDetailHarness(this, startMillis = vnMillis(2026, 10, 26, 22, 59))
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue(closeTime = "2300")))
        runCurrent()
        assertEquals(OpeningStatus.Open(TimeOfDay.parse("2300")!!), (vm.uiState.value.header as Section.Content).data.opening)
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(OpeningStatus.Closed(TimeOfDay.parse("0500")!!), (vm.uiState.value.header as Section.Content).data.opening)
    }

    @Test
    fun `khung gio het han khi dang mo man hinh thi bo khung gio`() = runTest {
        val h = CourtDetailHarness(this, date = "20261026", start = "2030", end = "2130")
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        assertEquals(TimeOfDay.parse("2030"), vm.uiState.value.window?.start)

        advanceTimeBy(31 * 60_000L)
        runCurrent()
        assertNull(vm.uiState.value.window)
        vm.effects.test {
            vm.onBookClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.OpenBookingGrid("demo-truong-chinh-909", null), awaitItem())
        }
    }

    @Test
    fun `khong con nguoi thu thap qua 5 giay thi go listener venue`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        val job = with(h) { vm.collectIn() }
        runCurrent()
        assertEquals(1, h.repository.venueSubscribers.value)
        job.cancel()
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(1, h.repository.venueSubscribers.value)
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(0, h.repository.venueSubscribers.value)
    }
}

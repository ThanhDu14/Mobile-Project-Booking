package com.example.booking_app.feature.courtdetail.ui

import app.cash.turbine.test
import com.example.booking_app.core.model.VenueRatingSummary
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.courtdetail.SeedPriceRules
import com.example.booking_app.feature.courtdetail.domain.model.PendingAction
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.example.booking_app.feature.courtdetail.testVenue
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** US6: cơ sở tạm ngừng, không còn tồn tại, đổi trạng thái khi đang xem (FR-021 → FR-023, SC-005). */
class CourtDetailViewModelUs6Test {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val rated = VenueRatingSummary(average = 4.5, count = 2, courtAvg = null, cleanlinessAvg = null, serviceAvg = null)

    @Test
    fun `tam ngung thi an dat san va chat, bang gia va danh gia van tai`() = runTest {
        val h = CourtDetailHarness(this)
        h.repository.priceRules = { SeedPriceRules.truongChinh }
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue(availability = VenueAvailability.SUSPENDED, rating = rated)))
        runCurrent()
        val state = vm.uiState.value
        assertEquals(VenueAvailability.SUSPENDED, state.availability)
        assertEquals(BottomBarState.Suspended, state.bottomBar)
        assertTrue((state.header as Section.Content).data.suspended)
        assertTrue(state.prices is Section.Content)
        assertTrue(state.reviews is Section.Content)

        vm.effects.test {
            vm.onChatClicked()
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `tam ngung va chua yeu thich thi bam trai tim khong lam gi`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue(availability = VenueAvailability.SUSPENDED)))
        runCurrent()
        vm.onFavoriteClicked()
        runCurrent()
        assertTrue(h.favorites.calls.isEmpty())
        assertEquals(false, vm.uiState.value.isFavorite)
    }

    @Test
    fun `tam ngung va dang yeu thich thi bam trai tim bo yeu thich`() = runTest {
        val h = CourtDetailHarness(this)
        h.favorites.favorite.value = true
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue(availability = VenueAvailability.SUSPENDED)))
        runCurrent()
        vm.onFavoriteClicked()
        runCurrent()
        assertEquals(listOf(false), h.favorites.calls.map { it.second })
        assertEquals(false, vm.uiState.value.isFavorite)
    }

    @Test
    fun `khong con ton tai thi bo yeu thich tu man hinh rieng`() = runTest {
        val h = CourtDetailHarness(this)
        h.favorites.favorite.value = true
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Gone)
        runCurrent()
        assertEquals(VenueAvailability.GONE, vm.uiState.value.availability)
        assertEquals(true, vm.uiState.value.isFavorite)
        assertEquals(BottomBarState.Hidden, vm.uiState.value.bottomBar)

        vm.onRemoveFavoriteFromGone()
        runCurrent()
        assertEquals("demo-truong-chinh-909", h.favorites.calls.single().first.venueId)
        assertEquals(false, h.favorites.calls.single().second)
        assertEquals(false, vm.uiState.value.isFavorite)
    }

    @Test
    fun `khong con ton tai va chua yeu thich thi nut bo yeu thich khong ghi`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Gone)
        runCurrent()
        vm.onRemoveFavoriteFromGone()
        runCurrent()
        assertTrue(h.favorites.calls.isEmpty())
    }

    @Test
    fun `khach mo co so tam ngung thi man hinh rut gon, khong doc courts priceRules reviews`() = runTest {
        val h = CourtDetailHarness(this, session = SessionState.SignedOut(null))
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.PermissionDenied)
        runCurrent()
        assertEquals(VenueAvailability.SUSPENDED_GUEST, vm.uiState.value.availability)
        assertEquals(0, h.repository.loadCourtsCalls)
        assertEquals(0, h.repository.loadPriceRulesCalls)
        assertEquals(0, h.reviews.calls)
    }

    @Test
    fun `nguoi da dang nhap bi Rules tu choi thi loi chung, khong crash`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.PermissionDenied)
        runCurrent()
        assertEquals(Section.Error(AppError.Unknown(null)), vm.uiState.value.header)
        assertEquals(BottomBarState.Hidden, vm.uiState.value.bottomBar)
    }

    @Test
    fun `khach dang nhap tu man hinh rut gon thi doc lai co so`() = runTest {
        val h = CourtDetailHarness(this, session = SessionState.SignedOut(null))
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.PermissionDenied)
        runCurrent()
        vm.effects.test {
            vm.onLoginClicked()
            assertEquals(CourtDetailEffect.OpenLogin, awaitItem())
        }
        vm.onScreenHidden()
        h.signIn()
        val before = h.repository.observeCalls
        vm.onScreenVisible()
        runCurrent()
        assertEquals(before + 1, h.repository.observeCalls)
    }

    @Test
    fun `dang xem thi co so bi khoa, trang thai doi ngay`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        assertTrue(vm.uiState.value.bottomBar is BottomBarState.Book)

        h.repository.emit(VenueResult.Found(testVenue(availability = VenueAvailability.SUSPENDED)))
        runCurrent()
        assertEquals(VenueAvailability.SUSPENDED, vm.uiState.value.availability)
        assertEquals(BottomBarState.Suspended, vm.uiState.value.bottomBar)
    }

    @Test
    fun `bam dat san ma server bao da khoa thi tu choi, khong mo luoi gio`() = runTest {
        val h = CourtDetailHarness(this)
        h.repository.serverStatus = { VenueAvailability.SUSPENDED }
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        vm.effects.test {
            vm.onBookClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.Snackbar(CourtDetailMessage.SUSPENDED), awaitItem())
            expectNoEvents()
        }
        assertEquals(BottomBarState.Suspended, vm.uiState.value.bottomBar)
    }

    @Test
    fun `thao tac cho dat san voi co so vua bi khoa thi khong mo luoi gio`() = runTest {
        val h = CourtDetailHarness(this, session = SessionState.SignedOut(null))
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        vm.onLoginChosen(PendingAction.BOOK)
        vm.onScreenHidden()
        h.repository.serverStatus = { VenueAvailability.SUSPENDED }
        h.signIn()
        vm.effects.test {
            awaitItem() // OpenLogin
            vm.onScreenVisible()
            runCurrent()
            assertEquals(CourtDetailEffect.Snackbar(CourtDetailMessage.SUSPENDED), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `thao tac cho yeu thich voi co so tam ngung thi khong them`() = runTest {
        val h = CourtDetailHarness(this, session = SessionState.SignedOut(null))
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue(availability = VenueAvailability.SUSPENDED)))
        runCurrent()
        vm.onLoginChosen(PendingAction.FAVORITE)
        vm.onScreenHidden()
        h.signIn()
        runCurrent()
        vm.onScreenVisible()
        runCurrent()
        assertTrue(h.favorites.calls.isEmpty())
    }
}

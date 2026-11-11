package com.example.booking_app.feature.courtdetail.ui

import app.cash.turbine.test
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.courtdetail.SeedPriceRules
import com.example.booking_app.feature.courtdetail.domain.model.Court
import com.example.booking_app.feature.courtdetail.domain.model.PendingAction
import com.example.booking_app.feature.courtdetail.domain.model.SurfaceType
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.example.booking_app.feature.courtdetail.testVenue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** US5: khách chưa đăng nhập, thao tác đang chờ (research R11), chủ cơ sở (FR-018), yêu thích. */
class CourtDetailViewModelUs5Test {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun TestScope.guest() = CourtDetailHarness(this, session = SessionState.SignedOut(null))

    private suspend fun TestScope.open(h: CourtDetailHarness, ownerId: String = "demo-owner-1"): CourtDetailViewModel {
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue(ownerId = ownerId)))
        runCurrent()
        vm.onScreenVisible()
        runCurrent()
        return vm
    }

    @Test
    fun `khach bam dat san, trai tim, chat thi moi dang nhap, khong goi repository`() = runTest {
        val h = guest()
        val vm = open(h)
        vm.effects.test {
            vm.onBookClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.ShowLoginPrompt(PendingAction.BOOK), awaitItem())
            vm.onFavoriteClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.ShowLoginPrompt(PendingAction.FAVORITE), awaitItem())
            vm.onChatClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.ShowLoginPrompt(PendingAction.CHAT), awaitItem())
        }
        assertEquals(0, h.repository.fetchStatusCalls)
        assertTrue(h.favorites.calls.isEmpty())
        assertEquals(0, h.favorites.observeCalls)
        assertNull(vm.uiState.value.isFavorite)
    }

    @Test
    fun `chon dang nhap thi ghi pendingAction va mo dang nhap`() = runTest {
        val h = guest()
        val vm = open(h)
        vm.effects.test {
            vm.onLoginChosen(PendingAction.BOOK)
            runCurrent()
            assertEquals(CourtDetailEffect.OpenLogin, awaitItem())
        }
        assertEquals("BOOK", h.handle.get<String>(CourtDetailViewModel.KEY_PENDING_ACTION))
    }

    @Test
    fun `dang nhap xong quay lai thi dat san chay dung mot lan`() = runTest {
        val h = guest()
        val vm = open(h)
        vm.effects.test {
            vm.onLoginChosen(PendingAction.BOOK)
            runCurrent()
            assertEquals(CourtDetailEffect.OpenLogin, awaitItem())
            vm.onScreenHidden()
            h.signIn()
            runCurrent()
            vm.onScreenVisible()
            runCurrent()
            assertEquals(CourtDetailEffect.OpenBookingGrid("demo-truong-chinh-909", null), awaitItem())
            vm.onScreenVisible()
            runCurrent()
            expectNoEvents()
        }
        assertEquals(1, h.repository.fetchStatusCalls)
        assertNull(h.handle.get<String>(CourtDetailViewModel.KEY_PENDING_ACTION))
    }

    @Test
    fun `dang nhap xong quay lai thi luu yeu thich mot lan`() = runTest {
        val h = guest()
        val vm = open(h)
        vm.onLoginChosen(PendingAction.FAVORITE)
        vm.onScreenHidden()
        h.signIn()
        runCurrent()
        vm.onScreenVisible()
        runCurrent()
        vm.onScreenVisible()
        runCurrent()
        assertEquals(1, h.favorites.calls.size)
        assertEquals(true, h.favorites.calls.single().second)
        assertEquals("demo-truong-chinh-909", h.favorites.calls.single().first.venueId)
        assertEquals(true, vm.uiState.value.isFavorite)
    }

    @Test
    fun `dang nhap xong quay lai thi mo chat`() = runTest {
        val h = guest()
        val vm = open(h)
        vm.effects.test {
            vm.onLoginChosen(PendingAction.CHAT)
            assertEquals(CourtDetailEffect.OpenLogin, awaitItem())
            vm.onScreenHidden()
            h.signIn()
            runCurrent()
            vm.onScreenVisible()
            runCurrent()
            assertEquals(CourtDetailEffect.OpenChat("demo-truong-chinh-909"), awaitItem())
        }
    }

    @Test
    fun `van la khach khi quay lai thi huy thao tac cho, khong co effect`() = runTest {
        val h = guest()
        val vm = open(h)
        vm.effects.test {
            vm.onLoginChosen(PendingAction.BOOK)
            assertEquals(CourtDetailEffect.OpenLogin, awaitItem())
            vm.onScreenHidden()
            vm.onScreenVisible()
            runCurrent()
            expectNoEvents()
        }
        assertNull(h.handle.get<String>(CourtDetailViewModel.KEY_PENDING_ACTION))
        assertEquals(0, h.repository.fetchStatusCalls)
    }

    @Test
    fun `tao lai ViewModel tu SavedStateHandle van chay thao tac cho`() = runTest {
        val h = CourtDetailHarness(this)
        h.handle[CourtDetailViewModel.KEY_PENDING_ACTION] = "BOOK"
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        vm.effects.test {
            // Màn hình hiện trước khi venue tới: chờ venue rồi mới chạy.
            vm.onScreenVisible()
            runCurrent()
            expectNoEvents()
            h.repository.emit(VenueResult.Found(testVenue()))
            runCurrent()
            assertEquals(CourtDetailEffect.OpenBookingGrid("demo-truong-chinh-909", null), awaitItem())
        }
    }

    @Test
    fun `chu co so vua dang nhap thi dat san bi tu choi`() = runTest {
        val h = guest()
        val vm = open(h, ownerId = "owner-9")
        vm.effects.test {
            vm.onLoginChosen(PendingAction.BOOK)
            assertEquals(CourtDetailEffect.OpenLogin, awaitItem())
            vm.onScreenHidden()
            h.signIn(uid = "owner-9")
            runCurrent()
            vm.onScreenVisible()
            runCurrent()
            assertEquals(CourtDetailEffect.Snackbar(CourtDetailMessage.OWN_VENUE), awaitItem())
        }
        assertEquals(0, h.repository.fetchStatusCalls)
    }

    @Test
    fun `chu co so o che do nguoi choi bam dat san hoac chat thi bao day la co so cua ban`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h, ownerId = "player1")
        vm.effects.test {
            vm.onBookClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.Snackbar(CourtDetailMessage.OWN_VENUE), awaitItem())
            vm.onChatClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.Snackbar(CourtDetailMessage.OWN_VENUE), awaitItem())
        }
    }

    @Test
    fun `nguoi choi bam chat thi mo chat`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h)
        vm.effects.test {
            vm.onChatClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.OpenChat("demo-truong-chinh-909"), awaitItem())
        }
    }

    @Test
    fun `bam trai tim doi ngay va bao da them`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h)
        assertEquals(false, vm.uiState.value.isFavorite)
        vm.effects.test {
            vm.onFavoriteClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.Snackbar(CourtDetailMessage.FAVORITE_ADDED), awaitItem())
        }
        assertEquals(true, vm.uiState.value.isFavorite)
        assertEquals(listOf(true), h.favorites.calls.map { it.second })
    }

    @Test
    fun `ghi yeu thich loi thi tra lai trang thai cu va bao loi`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h)
        h.favorites.failNext = AppError.Network
        vm.effects.test {
            vm.onFavoriteClicked()
            runCurrent()
            assertEquals(CourtDetailEffect.Snackbar(CourtDetailMessage.FAVORITE_ADDED), awaitItem())
            assertEquals(CourtDetailEffect.Snackbar(CourtDetailMessage.FAVORITE_FAILED), awaitItem())
        }
        assertEquals(false, vm.uiState.value.isFavorite)
    }

    @Test
    fun `khach thay du moi muc nhu nguoi da dang nhap`() = runTest {
        val h = guest()
        h.repository.courts = { listOf(Court("c1", SurfaceType.PU, true)) }
        h.repository.priceRules = { SeedPriceRules.truongChinh }
        val vm = open(h)
        val state = vm.uiState.value
        assertTrue(state.header is Section.Content)
        assertTrue(state.courts is Section.Content)
        assertTrue(state.prices is Section.Content)
        assertTrue(state.bottomBar is BottomBarState.Book)
    }
}

package com.example.booking_app.feature.courtdetail.ui

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.courtdetail.SeedPriceRules
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.example.booking_app.feature.courtdetail.testVenue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CourtDetailViewModelUs2Test {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private suspend fun TestScope.open(h: CourtDetailHarness): CourtDetailViewModel {
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(testVenue()))
        runCurrent()
        return vm
    }

    private fun CourtDetailUiState.bottomPrice() = (bottomBar as BottomBarState.Book).price

    @Test
    fun `prices loi chi muc gia loi, thu lai chi goi loadPriceRules`() = runTest {
        val h = CourtDetailHarness(this)
        h.repository.priceRules = { throw AppError.Network }
        val vm = open(h)
        assertEquals(Section.Error(AppError.Network), vm.uiState.value.prices)
        assertTrue(vm.uiState.value.header is Section.Content)

        val courtCalls = h.repository.loadCourtsCalls
        h.repository.priceRules = { SeedPriceRules.truongChinh }
        vm.onRetry(SectionId.PRICES)
        runCurrent()
        assertTrue(vm.uiState.value.prices is Section.Content)
        assertEquals(2, h.repository.loadPriceRulesCalls)
        assertEquals(courtCalls, h.repository.loadCourtsCalls)
    }

    @Test
    fun `khong co rule thi Empty va thanh duoi Lien he`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h)
        assertEquals(Section.Empty, vm.uiState.value.prices)
        assertEquals(BottomPrice.Contact, vm.uiState.value.bottomPrice())
    }

    @Test
    fun `co khung thi thanh duoi la gia cua khung`() = runTest {
        val h = CourtDetailHarness(this, date = "20261027", start = "1900", end = "2100")
        h.repository.priceRules = { SeedPriceRules.truongChinh }
        val vm = open(h)
        val price = vm.uiState.value.bottomPrice() as BottomPrice.ForWindow
        assertEquals(130_000L, price.perHour)
        assertEquals(vm.uiState.value.window, price.window)
        val content = (vm.uiState.value.prices as Section.Content).data
        assertEquals(130_000L, content.selected!!.perHour)
    }

    @Test
    fun `khong co khung thi thanh duoi la tu gia thap nhat`() = runTest {
        val h = CourtDetailHarness(this)
        h.repository.priceRules = { SeedPriceRules.truongChinh }
        val vm = open(h)
        assertEquals(BottomPrice.From(50_000L), vm.uiState.value.bottomPrice())
        assertNull((vm.uiState.value.prices as Section.Content).data.selected)
    }

    @Test
    fun `khung ngoai bang gia thi thanh duoi Lien he`() = runTest {
        // Thứ ba 27/10 03:00–05:00: trước giờ mở cửa, không có dòng giá.
        val h = CourtDetailHarness(this, date = "20261027", start = "0300", end = "0500")
        h.repository.priceRules = { SeedPriceRules.truongChinh }
        val vm = open(h)
        assertEquals(BottomPrice.Contact, vm.uiState.value.bottomPrice())
    }

    @Test
    fun `khung het han khi dang mo thi bo to, an the Khung ban chon, thanh duoi ve tu X`() = runTest {
        // Bắt đầu thứ hai 26/10 20:00, khung thứ ba 27/10 19:00.
        val h = CourtDetailHarness(this, date = "20261027", start = "1900", end = "2100")
        h.repository.priceRules = { SeedPriceRules.truongChinh }
        val vm = open(h)
        assertTrue(vm.uiState.value.bottomPrice() is BottomPrice.ForWindow)

        advanceTimeBy(23 * 60 * 60_000L + 60_000L)
        runCurrent()
        val state = vm.uiState.value
        assertNull(state.window)
        val content = (state.prices as Section.Content).data
        assertNull(content.selected)
        assertTrue(content.groups.all { g -> g.rows.none { it.highlighted } })
        assertEquals(BottomPrice.From(50_000L), state.bottomPrice())
    }
}

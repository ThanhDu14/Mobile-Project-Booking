package com.example.booking_app.feature.courtdetail.domain.usecase

import com.example.booking_app.core.pricing.PriceVectors
import com.example.booking_app.feature.courtdetail.FakeVenueDetailRepository
import com.example.booking_app.feature.courtdetail.SeedPriceRules
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadPriceTableUseCaseTest {

    private val repository = FakeVenueDetailRepository()
    private val useCase = LoadPriceTableUseCase(repository)

    @Test
    fun `ca P1 den P9 ra dung gia moi gio va dong to (SC-002)`() {
        val cases = PriceVectors.load().cases
        assertEquals(9, cases.size)
        cases.forEach { c ->
            val table = useCase.table(c.rules, SearchWindow(c.date, c.start, c.end))
            assertEquals("${c.id} đ/giờ", c.expectedPerHour, table.selected?.perHour)
            val highlighted = table.groups.flatMap { g -> g.rows.filter { it.highlighted }.map { it.ruleId } }.toSet()
            assertEquals("${c.id} dòng tô", c.highlightedRuleIndexes.map { c.rules[it].id }.toSet(), highlighted)
        }
    }

    @Test
    fun `P3 la Lien he`() {
        val p3 = PriceVectors.load().cases.single { it.id == "P3" }
        val table = useCase.table(p3.rules, SearchWindow(p3.date, p3.start, p3.end))
        assertNull(table.selected!!.perHour)
    }

    @Test
    fun `khong co khung thi khong co SelectedWindowPrice, minPerHour la gia thap nhat`() {
        val table = useCase.table(SeedPriceRules.truongChinh, null)
        assertNull(table.selected)
        assertEquals(50_000L, table.minPerHour)
    }

    @Test
    fun `load doc priceRules tu repository`() = runTest {
        repository.priceRules = { SeedPriceRules.sonTa }
        val table = useCase.load("demo-son-ta", null)
        assertEquals(1, repository.loadPriceRulesCalls)
        assertEquals(3, table.groups.size)
        assertEquals(40_000L, table.minPerHour)
    }

    @Test
    fun `khong co rule thi bang rong, minPerHour null`() {
        val table = useCase.table(emptyList(), null)
        assertEquals(emptyList<Any>(), table.groups)
        assertNull(table.minPerHour)
    }
}

package com.example.booking_app.feature.courtdetail.ui

import app.cash.turbine.test
import com.example.booking_app.core.location.DistanceFormatter
import com.example.booking_app.core.location.GeoDistance
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.courtdetail.domain.model.PhoneDisplay
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.example.booking_app.feature.courtdetail.domain.model.VenueDetail
import com.example.booking_app.feature.courtdetail.testVenue
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CourtDetailViewModelUs3Test {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private suspend fun TestScope.open(h: CourtDetailHarness, venue: VenueDetail = testVenue()): CourtDetailViewModel {
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        h.repository.emit(VenueResult.Found(venue))
        runCurrent()
        return vm
    }

    @Test
    fun `so goi duoc thi bam ra Dial`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h, testVenue(phone = "0901 234 567"))
        assertEquals(PhoneDisplay.Dialable("+84901234567", "0901 234 567"), vm.uiState.value.phone)
        vm.effects.test {
            vm.onPhoneClicked()
            assertEquals(CourtDetailEffect.Dial("+84901234567"), awaitItem())
            vm.onPhoneLongClicked()
            assertEquals(CourtDetailEffect.CopyPhone("0901 234 567"), awaitItem())
        }
    }

    @Test
    fun `so bi che bam khong co effect, nhan giu thi sao chep`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h, testVenue(phone = "0944xxx486"))
        assertEquals(PhoneDisplay.DisplayOnly("0944xxx486"), vm.uiState.value.phone)
        vm.effects.test {
            vm.onPhoneClicked()
            runCurrent()
            expectNoEvents()
            vm.onPhoneLongClicked()
            assertEquals(CourtDetailEffect.CopyPhone("0944xxx486"), awaitItem())
        }
    }

    @Test
    fun `khong co so thi phone null`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h, testVenue(phone = null))
        assertNull(vm.uiState.value.phone)
    }

    @Test
    fun `khong co toa do thi map Empty`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h, testVenue(location = null))
        assertEquals(Section.Empty, vm.uiState.value.map)
    }

    @Test
    fun `chi duong ra OpenDirections`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h)
        vm.effects.test {
            vm.onDirectionsClicked()
            assertEquals(CourtDetailEffect.OpenDirections(10.8128, 106.6283, "CLB Cầu Lông Trường Chinh 909"), awaitItem())
        }
    }

    @Test
    fun `co quyen vi tri thi hien khoang cach`() = runTest {
        val h = CourtDetailHarness(this)
        val me = GeoPoint(10.8228, 106.6283)
        h.location.permission = true
        h.location.location = me
        val vm = open(h)
        val expected = DistanceFormatter.format(GeoDistance.meters(me, GeoPoint(10.8128, 106.6283)))
        assertEquals(expected, vm.uiState.value.distanceText)
    }

    @Test
    fun `khong co quyen thi khong goi current va khong xin quyen`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h)
        vm.effects.test {
            runCurrent()
            expectNoEvents()
        }
        assertEquals(0, h.location.currentCalls)
        assertNull(vm.uiState.value.distanceText)
    }
}

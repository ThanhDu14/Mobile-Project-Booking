package com.example.booking_app.feature.courtdetail.ui

import app.cash.turbine.test
import com.example.booking_app.core.model.VenueRatingSummary
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.courtdetail.domain.model.VenueDetail
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.example.booking_app.feature.courtdetail.testReview
import com.example.booking_app.feature.courtdetail.testVenue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** T054: mục đánh giá tổng quan (FR-014, FR-015, FR-024, research R3). */
class CourtDetailViewModelUs4Test {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val rated = VenueRatingSummary(4.3, 11, 4.5, 4.2, 4.1)

    private suspend fun TestScope.open(h: CourtDetailHarness, venue: VenueDetail = testVenue(rating = rated)): CourtDetailViewModel {
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        runCurrent()
        h.repository.emit(VenueResult.Found(venue))
        runCurrent()
        return vm
    }

    @Test
    fun `chi tai danh gia sau khi phan dau co noi dung`() = runTest {
        val h = CourtDetailHarness(this)
        h.reviews.reviews = listOf(testReview("r1"))
        val vm = h.viewModel()
        with(h) { vm.collectIn() }
        runCurrent()
        assertEquals(0, h.reviews.calls)
        assertEquals(Section.Loading, vm.uiState.value.reviews)

        h.repository.emit(VenueResult.Found(testVenue(rating = rated)))
        runCurrent()
        assertTrue(vm.uiState.value.header is Section.Content)
        assertEquals(1, h.reviews.calls)
        assertEquals(Section.Content(ReviewsContent(rated, listOf(testReview("r1")))), vm.uiState.value.reviews)
    }

    @Test
    fun `listener bao lai venue khong tai lai danh gia`() = runTest {
        val h = CourtDetailHarness(this)
        open(h)
        h.repository.emit(VenueResult.Found(testVenue(rating = rated, isFromCache = true)))
        runCurrent()
        assertEquals(1, h.reviews.calls)
    }

    @Test
    fun `chua co danh gia thi Empty va khong goi nguon`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h, testVenue(rating = VenueRatingSummary.EMPTY))
        assertEquals(Section.Empty, vm.uiState.value.reviews)
        assertEquals(0, h.reviews.calls)
    }

    @Test
    fun `khach cung tai danh gia sau khi co venue`() = runTest {
        val h = CourtDetailHarness(this, session = com.example.booking_app.core.session.SessionState.SignedOut(null))
        open(h)
        assertEquals(1, h.reviews.calls)
    }

    @Test
    fun `xem tat ca mo OpenReviews`() = runTest {
        val h = CourtDetailHarness(this)
        val vm = open(h)
        vm.effects.test {
            vm.onSeeAllReviewsClicked()
            assertEquals(CourtDetailEffect.OpenReviews("demo-truong-chinh-909", "CLB Cầu Lông Trường Chinh 909"), awaitItem())
        }
    }

    @Test
    fun `loi chi o muc danh gia, thu lai goi lai dung muc nay`() = runTest {
        val h = CourtDetailHarness(this)
        h.reviews.error = IOException("offline")
        val vm = open(h)
        assertEquals(Section.Error(AppError.Network), vm.uiState.value.reviews)
        assertTrue(vm.uiState.value.header is Section.Content)
        assertTrue(vm.uiState.value.courts !is Section.Error)
        assertTrue(vm.uiState.value.prices !is Section.Error)

        h.reviews.error = null
        h.reviews.reviews = listOf(testReview("r1"))
        vm.onRetry(SectionId.REVIEWS)
        runCurrent()
        assertEquals(2, h.reviews.calls)
        assertTrue(vm.uiState.value.reviews is Section.Content)
    }

    @Test
    fun `diem va tieu chi lay tu venue, khong tinh tu danh sach`() = runTest {
        val h = CourtDetailHarness(this)
        h.reviews.reviews = listOf(testReview("r1", overall = 1), testReview("r2", overall = 1))
        val vm = open(h)
        val content = (vm.uiState.value.reviews as Section.Content).data
        assertEquals(rated, content.summary)
    }

    @Test
    fun `bam anh trong danh gia mo trinh xem anh`() = runTest {
        val h = CourtDetailHarness(this)
        h.reviews.reviews = listOf(testReview("r1", photoUrls = listOf("https://a/r1.jpg", "https://a/r2.jpg")))
        val vm = open(h)
        vm.effects.test {
            vm.onReviewPhotoClicked("r1", 1)
            assertEquals(CourtDetailEffect.OpenPhotoViewer(listOf("https://a/r1.jpg", "https://a/r2.jpg"), 1), awaitItem())
        }
    }
}

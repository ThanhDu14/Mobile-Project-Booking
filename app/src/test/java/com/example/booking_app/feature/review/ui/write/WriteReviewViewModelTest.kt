package com.example.booking_app.feature.review.ui.write

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.review.COMPLETED_AT
import com.example.booking_app.feature.review.FakeReviewRepository
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.FakePhotoUploader
import com.example.booking_app.feature.review.domain.usecase.DeleteReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.UpdateReviewUseCase
import com.example.booking_app.feature.review.review
import com.example.booking_app.feature.review.reviewTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class WriteReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeReviewRepository()
    private var nowMs = COMPLETED_AT.plus(Duration.ofDays(1)).toEpochMilli()
    private val clock = object : AppClock {
        override fun now(): Long = nowMs
        override fun elapsedRealtime(): Long = 0
    }

    private fun viewModel() = WriteReviewViewModel(
        SavedStateHandle(mapOf("bookingId" to "b1", "mode" to WriteReviewMode.CREATE)),
        repository,
        SubmitReviewUseCase(repository),
        UpdateReviewUseCase(repository),
        DeleteReviewUseCase(repository),
        FakePhotoUploader(),
        clock,
    )

    private fun WriteReviewViewModel.rate(court: Int?, cleanliness: Int?, service: Int?) {
        court?.let { onRatingChanged(ReviewCriterion.COURT, it) }
        cleanliness?.let { onRatingChanged(ReviewCriterion.CLEANLINESS, it) }
        service?.let { onRatingChanged(ReviewCriterion.SERVICE, it) }
    }

    @Test
    fun `tai xong - co thong tin don, CAN_WRITE, chua co diem tong`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Sân Phú Thọ", state.target?.venueName)
        assertEquals(ReviewEligibility.CAN_WRITE, state.eligibility)
        assertNull(state.overall)
        assertEquals(COMPLETED_AT.plus(Duration.ofDays(7)), state.canEditUntil)
    }

    @Test
    fun `thieu tieu chi - bao loi dung tieu chi, khong goi repository`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.rate(5, 4, null)

        vm.submit()
        advanceUntilIdle()

        assertEquals(listOf(ReviewCriterion.SERVICE), vm.uiState.value.missingCriteria)
        assertTrue(repository.createCalls.isEmpty())
    }

    @Test
    fun `cham bo sung tieu chi con thieu - het bao loi tieu chi do`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.rate(5, null, null)
        vm.submit()
        advanceUntilIdle()

        vm.onRatingChanged(ReviewCriterion.CLEANLINESS, 4)

        assertEquals(listOf(ReviewCriterion.SERVICE), vm.uiState.value.missingCriteria)
    }

    @Test
    fun `gui thanh cong - su kien dieu huong va diem tong 4,0 cho 5-4-3`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.rate(5, 4, 3)
        assertEquals(4.0, vm.uiState.value.overall!!, 0.0)

        vm.events.test {
            vm.submit()
            assertEquals(WriteReviewEvent.Submitted(4.0), awaitItem())
        }
        assertEquals(1, repository.createCalls.size)
    }

    @Test
    fun `loi NETWORK - giu nguyen ban nhap, bao loi co Thu lai, thu lai thanh cong`() = runTest {
        repository.createError = ReviewException(ReviewError.NETWORK)
        val vm = viewModel()
        advanceUntilIdle()
        vm.rate(5, 4, 3)
        vm.onCommentChanged("Sân sạch")

        vm.events.test {
            vm.submit()
            assertEquals(WriteReviewEvent.SubmitFailed(ReviewError.NETWORK, canRetry = true), awaitItem())

            val draft = vm.uiState.value.draft
            assertEquals(Triple(5, 4, 3), Triple(draft.court, draft.cleanliness, draft.service))
            assertEquals("Sân sạch", draft.comment)
            assertFalse(vm.uiState.value.isSubmitting)

            repository.createError = null
            vm.submit()
            assertEquals(WriteReviewEvent.Submitted(4.0), awaitItem())
        }
    }

    @Test
    fun `bam Gui 3 lan lien tiep - chi 1 loi goi create`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val vm = viewModel()
        advanceUntilIdle()
        vm.rate(5, 4, 3)

        vm.submit()
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSubmitting)
        vm.submit()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.createCalls.size)
    }

    @Test
    fun `qua han 7 ngay - EXPIRED, khong gui duoc`() = runTest {
        nowMs = COMPLETED_AT.plus(Duration.ofDays(8)).toEpochMilli()
        val vm = viewModel()
        advanceUntilIdle()
        vm.rate(5, 4, 3)

        vm.submit()
        advanceUntilIdle()

        assertEquals(ReviewEligibility.EXPIRED, vm.uiState.value.eligibility)
        assertFalse(vm.uiState.value.canSubmit)
        assertTrue(repository.createCalls.isEmpty())
    }

    @Test
    fun `don da co danh gia - CAN_VIEW, hien noi dung da gui`() = runTest {
        repository.myReview.value = review("b1", comment = "Đèn sáng")
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ReviewEligibility.CAN_VIEW, state.eligibility)
        assertEquals("Đèn sáng", state.draft.comment)
        assertEquals(5, state.draft.court)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `tai don loi - loadError, thu lai thanh cong`() = runTest {
        repository.targetError = AppError.Network
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ReviewError.NETWORK, vm.uiState.value.loadError)

        repository.targetError = null
        vm.retryLoad()
        advanceUntilIdle()

        assertNull(vm.uiState.value.loadError)
        assertEquals("Sân Phú Thọ", vm.uiState.value.target?.venueName)
    }

    @Test
    fun `don chua hoan thanh - NOT_ELIGIBLE`() = runTest {
        repository.target = reviewTarget("b1", status = "CONFIRMED", completedAt = null)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ReviewEligibility.NOT_ELIGIBLE, vm.uiState.value.eligibility)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun `hasUnsavedChanges - chi true khi da nhap ma chua gui`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasUnsavedChanges)

        vm.onCommentChanged("abc")
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }
}

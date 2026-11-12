package com.example.booking_app.feature.review.ui.write

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.review.COMPLETED_AT
import com.example.booking_app.feature.review.FakePhotoUploader
import com.example.booking_app.feature.review.FakeReviewRepository
import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.model.ReviewTargetType
import com.example.booking_app.feature.review.domain.usecase.DeleteReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.UpdateReviewUseCase
import com.example.booking_app.feature.review.dropInReviewTarget
import com.example.booking_app.feature.review.review
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/** T059 (US5 spec 080): viết, xem và sửa đánh giá của một buổi vãng lai. */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteReviewDropInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeReviewRepository()
    private val uploader = FakePhotoUploader()
    private var nowMs = COMPLETED_AT.plus(Duration.ofDays(1)).toEpochMilli()
    private val clock = object : AppClock {
        override fun now(): Long = nowMs
        override fun elapsedRealtime(): Long = 0
    }

    private fun viewModel(mode: WriteReviewMode = WriteReviewMode.CREATE) = WriteReviewViewModel(
        SavedStateHandle(mapOf("sessionId" to "s1", "mode" to mode)),
        repository,
        SubmitReviewUseCase(repository),
        UpdateReviewUseCase(repository),
        DeleteReviewUseCase(repository),
        uploader,
        clock,
    )

    private fun WriteReviewViewModel.rateAll() {
        onRatingChanged(ReviewCriterion.COURT, 5)
        onRatingChanged(ReviewCriterion.CLEANLINESS, 4)
        onRatingChanged(ReviewCriterion.SERVICE, 3)
    }

    @Test
    fun `nap target tu buoi - CAN_WRITE, doc danh gia theo id session_uid`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("s1"), repository.dropInTargetCalls)
        assertEquals("Sân Hòa Bình", state.target?.venueName)
        assertEquals(ReviewEligibility.CAN_WRITE, state.eligibility)
        assertEquals(listOf("s1_u1"), repository.observedMyReviewIds)
        assertEquals(COMPLETED_AT.plus(Duration.ofDays(7)), state.canEditUntil)
    }

    @Test
    fun `gui danh gia - create gui sessionId, khong gui bookingId`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.rateAll()

        vm.events.test {
            vm.submit()
            assertEquals(WriteReviewEvent.Submitted(4.0), awaitItem())
        }
        val call = repository.createCalls.single()
        assertEquals("s1", call.sessionId)
        assertNull(call.bookingId)
    }

    @Test
    fun `anh tai len dung thu muc reviewId, bo anh gui discardPhotos kem sessionId`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.addPhotos(listOf("content://p/1"))
        advanceUntilIdle()

        val path = vm.uiState.value.draft.photos.single().remotePath
        assertEquals("player1/s1_u1/1.jpg", path)

        vm.removePhoto("content://p/1")
        advanceUntilIdle()
        assertEquals(listOf<String?>("s1"), repository.discardSessionIds)
    }

    @Test
    fun `luot chua check-in - NOT_ELIGIBLE, khong gui duoc`() = runTest {
        repository.dropInTarget = dropInReviewTarget("s1", status = "REGISTERED")
        val vm = viewModel()
        advanceUntilIdle()
        vm.rateAll()

        assertEquals(ReviewEligibility.NOT_ELIGIBLE, vm.uiState.value.eligibility)
        assertTrue(!vm.uiState.value.canSubmit)
    }

    @Test
    fun `qua 7 ngay sau endAt chua danh gia - EXPIRED`() = runTest {
        nowMs = COMPLETED_AT.plus(Duration.ofDays(8)).toEpochMilli()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ReviewEligibility.EXPIRED, vm.uiState.value.eligibility)
    }

    @Test
    fun `da danh gia - CAN_VIEW, mode EDIT sua duoc bang update voi reviewId`() = runTest {
        repository.myReviewsById["s1_u1"] = review("s1_u1", targetType = ReviewTargetType.DROP_IN)
        val vm = viewModel(WriteReviewMode.EDIT)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isEditing)
        vm.onRatingChanged(ReviewCriterion.COURT, 2)
        vm.submit()
        advanceUntilIdle()

        assertEquals("s1_u1", repository.updateCalls.single().bookingId)
        assertTrue(repository.createCalls.isEmpty())
    }

    @Test
    fun `loi NOT_CHECKED_IN - bao loi khong cho thu lai`() = runTest {
        repository.createError = ReviewException(ReviewError.NOT_CHECKED_IN)
        val vm = viewModel()
        advanceUntilIdle()
        vm.rateAll()

        vm.events.test {
            vm.submit()
            assertEquals(WriteReviewEvent.SubmitFailed(ReviewError.NOT_CHECKED_IN, canRetry = false), awaitItem())
        }
    }
}

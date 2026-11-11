package com.example.booking_app.feature.review.ui.write

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.review.COMPLETED_AT
import com.example.booking_app.feature.review.FakePhotoUploader
import com.example.booking_app.feature.review.FakeReviewRepository
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.model.DraftPhoto
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.usecase.DeleteReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.UpdateReviewUseCase
import com.example.booking_app.feature.review.review
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/** T063 (US5): sửa hoặc xóa đánh giá của mình. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeReviewRepository().apply {
        myReview.value = review("b1", Ratings(5, 4, 3), "Sân sạch", photoPaths = listOf("player1/b1/a.jpg"))
    }
    private val uploader = FakePhotoUploader()
    private var nowMs = COMPLETED_AT.plus(Duration.ofDays(2)).toEpochMilli()
    private val clock = object : AppClock {
        override fun now(): Long = nowMs
        override fun elapsedRealtime(): Long = 0
    }

    private fun viewModel(mode: WriteReviewMode = WriteReviewMode.EDIT) = WriteReviewViewModel(
        SavedStateHandle(mapOf("bookingId" to "b1", "mode" to mode)),
        repository,
        SubmitReviewUseCase(repository),
        UpdateReviewUseCase(repository),
        DeleteReviewUseCase(repository),
        uploader,
        clock,
    )

    @Test
    fun `mode EDIT - nap san du lieu, dang sua, co the Luu va Xoa`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isEditing)
        assertFalse(state.isReadOnly)
        assertTrue(state.canEdit)
        assertTrue(state.canDelete)
        assertEquals(5, state.draft.court)
        assertEquals("Sân sạch", state.draft.comment)
        val photo = state.draft.photos.single()
        assertEquals("player1/b1/a.jpg", photo.remotePath)
        assertEquals(DraftPhoto.State.UPLOADED, photo.state)
    }

    @Test
    fun `luu - goi update voi noi dung moi va anh cu, phat Saved`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onRatingChanged(ReviewCriterion.SERVICE, 5)

        vm.events.test {
            vm.submit()
            assertEquals(WriteReviewEvent.Saved(4.7), awaitItem())
        }
        val call = repository.updateCalls.single()
        assertEquals(Ratings(5, 4, 5), call.ratings)
        assertEquals(listOf("player1/b1/a.jpg"), call.photoPaths)
        assertTrue(repository.createCalls.isEmpty())
    }

    @Test
    fun `qua han - chi con Xoa, khong sua duoc`() = runTest {
        nowMs = COMPLETED_AT.plus(Duration.ofDays(8)).toEpochMilli()
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.canEdit)
        assertFalse(state.isEditing)
        assertTrue(state.isReadOnly)
        assertTrue(state.canDelete)
        assertFalse(state.canSubmit)

        vm.startEditing()
        vm.onRatingChanged(ReviewCriterion.SERVICE, 1)
        assertEquals(3, vm.uiState.value.draft.service)
    }

    @Test
    fun `mo de xem roi bam Sua trong han - chuyen sang dang sua`() = runTest {
        val vm = viewModel(WriteReviewMode.CREATE)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReadOnly)
        assertTrue(vm.uiState.value.canEdit)

        vm.startEditing()
        assertTrue(vm.uiState.value.isEditing)
        assertFalse(vm.uiState.value.isReadOnly)
    }

    @Test
    fun `xoa can xac nhan - requestDelete chi hoi, confirmDelete moi goi repository`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.requestDelete()
            assertEquals(WriteReviewEvent.ConfirmDelete, awaitItem())
            assertTrue(repository.deleteCalls.isEmpty())

            vm.confirmDelete()
            assertEquals(WriteReviewEvent.Deleted, awaitItem())
        }
        assertEquals(listOf("b1"), repository.deleteCalls)
    }

    @Test
    fun `xoa loi - bao DeleteFailed, khong dieu huong`() = runTest {
        repository.deleteError = ReviewException(ReviewError.NETWORK)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.confirmDelete()
            assertEquals(WriteReviewEvent.DeleteFailed(ReviewError.NETWORK), awaitItem())
        }
        assertFalse(vm.uiState.value.isDeleting)
    }

    @Test
    fun `sua - bo anh cu khong goi discardPhotos (server xoa sau khi luu)`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.removePhoto(vm.uiState.value.draft.photos.single().localUri)
        advanceUntilIdle()
        assertTrue(repository.discardCalls.isEmpty())

        vm.submit()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), repository.updateCalls.single().photoPaths)
    }
}

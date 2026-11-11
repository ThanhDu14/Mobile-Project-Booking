package com.example.booking_app.feature.review.ui.write

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.review.COMPLETED_AT
import com.example.booking_app.feature.review.FakePhotoUploader
import com.example.booking_app.feature.review.FakeReviewRepository
import com.example.booking_app.feature.review.domain.model.DraftPhoto
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.usecase.DeleteReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewUseCase
import com.example.booking_app.feature.review.domain.usecase.UpdateReviewUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/** T040 (US2): đính kèm ảnh khi viết đánh giá. */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteReviewPhotosTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeReviewRepository()
    private val uploader = FakePhotoUploader()
    private val clock = object : AppClock {
        override fun now(): Long = COMPLETED_AT.plus(Duration.ofDays(1)).toEpochMilli()
        override fun elapsedRealtime(): Long = 0
    }

    private fun viewModel() = WriteReviewViewModel(
        SavedStateHandle(mapOf("bookingId" to "b1", "mode" to WriteReviewMode.CREATE)),
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
    fun `chon anh thu 4 - bao review_photo_limit, chi giu 3 anh`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.addPhotos(listOf("content://p/1", "content://p/2"))
        advanceUntilIdle()

        vm.events.test {
            vm.addPhotos(listOf("content://p/3", "content://p/4"))
            assertEquals(WriteReviewEvent.PhotoLimit, awaitItem())
        }
        advanceUntilIdle()
        val draftPhotos = vm.uiState.value.draft.photos
        assertEquals(listOf("content://p/1", "content://p/2", "content://p/3"), draftPhotos.map { it.localUri })
        assertTrue(draftPhotos.all { it.state == DraftPhoto.State.UPLOADED })
        assertEquals(0, vm.uiState.value.remainingPhotoSlots)
    }

    @Test
    fun `anh dang tai - nut Gui bi khoa, tai xong thi mo`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.rateAll()
        uploader.gate = CompletableDeferred()

        vm.addPhotos(listOf("content://p/1"))
        advanceUntilIdle()
        assertEquals(DraftPhoto.State.UPLOADING, vm.uiState.value.draft.photos.single().state)
        assertFalse(vm.uiState.value.canSubmit)

        uploader.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `anh FAILED - khoa Gui cho toi khi thu lai thanh cong (FR-006)`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.rateAll()
        uploader.failing += "content://p/1"

        vm.addPhotos(listOf("content://p/1"))
        advanceUntilIdle()
        assertEquals(DraftPhoto.State.FAILED, vm.uiState.value.draft.photos.single().state)
        assertFalse(vm.uiState.value.canSubmit)
        vm.submit()
        advanceUntilIdle()
        assertTrue(repository.createCalls.isEmpty())

        uploader.failing.clear()
        vm.retryPhoto("content://p/1")
        advanceUntilIdle()
        val photo = vm.uiState.value.draft.photos.single()
        assertEquals(DraftPhoto.State.UPLOADED, photo.state)
        assertEquals("player1/b1/1.jpg", photo.remotePath)
        assertTrue(vm.uiState.value.canSubmit)

        vm.submit()
        advanceUntilIdle()
        assertEquals(listOf("player1/b1/1.jpg"), repository.createCalls.single().photoPaths)
    }

    @Test
    fun `anh FAILED - bo anh thi mo lai nut Gui`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.rateAll()
        uploader.failing += "content://p/1"
        vm.addPhotos(listOf("content://p/1"))
        advanceUntilIdle()

        vm.removePhoto("content://p/1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.draft.photos.isEmpty())
        assertTrue(vm.uiState.value.canSubmit)
        assertTrue("ảnh chưa tải lên thì không cần xóa trên server", repository.discardCalls.isEmpty())
    }

    @Test
    fun `bo anh da tai - goi discardPhotos voi path cua anh`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.addPhotos(listOf("content://p/1", "content://p/2"))
        advanceUntilIdle()

        vm.removePhoto("content://p/1")
        advanceUntilIdle()
        assertEquals(listOf("b1" to listOf("player1/b1/1.jpg")), repository.discardCalls)
        assertEquals(listOf("content://p/2"), vm.uiState.value.draft.photos.map { it.localUri })
    }

    @Test
    fun `anh qua 2 MB sau nen - danh dau FAILED va bao PhotoTooLarge`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        uploader.tooLarge += "content://p/big"

        vm.events.test {
            vm.addPhotos(listOf("content://p/big"))
            assertEquals(WriteReviewEvent.PhotoTooLarge, awaitItem())
        }
        assertEquals(DraftPhoto.State.FAILED, vm.uiState.value.draft.photos.single().state)
    }

    @Test
    fun `bo danh gia dang viet - xoa moi anh da tai len`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.addPhotos(listOf("content://p/1", "content://p/2"))
        advanceUntilIdle()

        vm.discardDraft()
        advanceUntilIdle()
        assertEquals(listOf("b1" to listOf("player1/b1/1.jpg", "player1/b1/2.jpg")), repository.discardCalls)
    }
}

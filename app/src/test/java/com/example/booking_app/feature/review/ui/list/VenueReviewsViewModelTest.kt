package com.example.booking_app.feature.review.ui.list

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.feature.review.COMPLETED_AT
import com.example.booking_app.feature.review.FakeReviewRepository
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.model.Review
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

/** T067 (US6): xem tất cả đánh giá của một sân. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenueReviewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 25 đánh giá: r0 mới nhất; r0–r4 có ảnh; overall 5,4,3,2,1 lặp lại. */
    private val repository = FakeReviewRepository().apply {
        reviews.value = (0 until 25).map { i ->
            review(
                id = "r$i",
                overall = (5 - i % 5).toDouble(),
                createdAt = COMPLETED_AT.minus(Duration.ofHours(i.toLong())),
                photoPaths = if (i < 5) listOf("u/r$i/a.jpg") else emptyList(),
            )
        }
    }

    private fun viewModel(venueId: String = "venueA") = VenueReviewsViewModel(
        SavedStateHandle(mapOf("venueId" to venueId, "venueName" to "Sân A")),
        repository,
    )

    private fun VenueReviewsViewModel.items(): List<Review> =
        (uiState.value.reviews as? UiState.Content<List<Review>>)?.data.orEmpty()

    @Test
    fun `trang dau 20, moi nhat truoc - cuon cuoi tai them`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(20, vm.items().size)
        assertEquals("r0", vm.items().first().id)
        assertEquals(4.0, vm.uiState.value.summary?.average)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(25, vm.items().size)
        assertEquals(40, repository.venueQueries.last().limit)

        val queries = repository.venueQueries.size
        vm.loadMore()
        advanceUntilIdle()
        assertEquals("đã hết đánh giá thì không truy vấn thêm", queries, repository.venueQueries.size)
    }

    @Test
    fun `loc 4 sao dung starBucket == 4`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setFilter(ReviewFilter.Stars(4))
        advanceUntilIdle()
        val query = repository.venueQueries.last()
        assertEquals(4, query.stars)
        assertEquals(false, query.withPhotos)
        assertEquals(20, query.limit)
        assertTrue(vm.items().all { it.overall == 4.0 })
        assertEquals(5, vm.items().size)
    }

    @Test
    fun `loc Co anh`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setFilter(ReviewFilter.WithPhotos)
        advanceUntilIdle()
        val query = repository.venueQueries.last()
        assertNull(query.stars)
        assertTrue(query.withPhotos)
        assertEquals(listOf("r0", "r1", "r2", "r3", "r4"), vm.items().map { it.id })
    }

    @Test
    fun `san chua co danh gia - Empty`() = runTest {
        val vm = viewModel("venueEmpty")
        advanceUntilIdle()
        assertEquals(UiState.Empty, vm.uiState.value.reviews)
    }

    @Test
    fun `loi - Error co thu lai`() = runTest {
        repository.listError = ReviewException(ReviewError.NETWORK)
        val vm = viewModel()
        advanceUntilIdle()
        val error = vm.uiState.value.reviews as UiState.Error
        assertTrue(error.retry != null)

        repository.listError = null
        error.retry!!.invoke()
        advanceUntilIdle()
        assertEquals(20, vm.items().size)
    }
}

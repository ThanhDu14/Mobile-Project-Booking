package com.example.booking_app.feature.review.ui.owner

import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.feature.review.COMPLETED_AT
import com.example.booking_app.feature.review.FakeReviewRepository
import com.example.booking_app.feature.review.domain.OwnerVenuesProvider
import com.example.booking_app.feature.review.domain.model.OwnerReply
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.VenueOption
import com.example.booking_app.feature.review.domain.usecase.ReplyToReviewUseCase
import com.example.booking_app.feature.review.review
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/** T056 (US4): chủ sân xem và phản hồi đánh giá của các cơ sở mình. */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerReviewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeReviewRepository().apply {
        val reply = OwnerReply("Cảm ơn", COMPLETED_AT, isEdited = false)
        reviews.value = listOf(
            review("a1", venueId = "venueA", createdAt = COMPLETED_AT.plus(Duration.ofHours(3))),
            review("a2", venueId = "venueA", createdAt = COMPLETED_AT.plus(Duration.ofHours(2)), ownerReply = reply),
            review("c1", venueId = "venueC", createdAt = COMPLETED_AT.plus(Duration.ofHours(1))),
            review("x1", venueId = "venueX"),
        )
    }
    private var venuesError: Throwable? = null
    private val venues = object : OwnerVenuesProvider {
        override suspend fun ownedVenues(): List<VenueOption> {
            venuesError?.let { throw it }
            return listOf(VenueOption("venueA", "Sân A"), VenueOption("venueC", "Sân C"))
        }
    }

    private fun viewModel() = OwnerReviewsViewModel(venues, repository, ReplyToReviewUseCase(repository))

    private fun OwnerReviewsViewModel.ids(): List<String> =
        (uiState.value.reviews as? UiState.Content<List<Review>>)?.data.orEmpty().map { it.id }

    @Test
    fun `mac dinh - danh gia moi nhat cua moi co so cua minh`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("a1", "a2", "c1"), vm.ids())
        assertEquals(listOf("venueA", "venueC"), repository.ownerQueries.last().venueIds)
        assertEquals(2, vm.uiState.value.venues.size)
    }

    @Test
    fun `loc theo co so va Chua phan hoi`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectVenue("venueA")
        advanceUntilIdle()
        assertEquals(listOf("a1", "a2"), vm.ids())
        assertEquals(listOf("venueA"), repository.ownerQueries.last().venueIds)

        vm.setUnrepliedOnly(true)
        advanceUntilIdle()
        assertEquals(listOf("a1"), vm.ids())
        assertTrue(repository.ownerQueries.last().unrepliedOnly)

        vm.selectVenue(null)
        advanceUntilIdle()
        assertEquals(listOf("a1", "c1"), vm.ids())
    }

    @Test
    fun `gui phan hoi rong - loi, khong goi repository`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.submitReply("a1", "   ")
            assertEquals(OwnerReviewsEvent.ReplyInvalid(empty = true, tooLong = false), awaitItem())
        }
        assertTrue(repository.replyCalls.isEmpty())
    }

    @Test
    fun `gui phan hoi qua 500 ky tu - loi, khong goi repository`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.submitReply("a1", "x".repeat(501))
            assertEquals(OwnerReviewsEvent.ReplyInvalid(empty = false, tooLong = true), awaitItem())
        }
        assertTrue(repository.replyCalls.isEmpty())
    }

    @Test
    fun `gui phan hoi hop le - goi reply va phat ReplySent`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.submitReply("a1", "  Cảm ơn bạn  ")
            assertEquals(OwnerReviewsEvent.ReplySent, awaitItem())
        }
        assertEquals(listOf("a1" to "Cảm ơn bạn"), repository.replyCalls)
    }

    @Test
    fun `chu san chua co co so - Empty`() = runTest {
        val vm = OwnerReviewsViewModel(
            object : OwnerVenuesProvider {
                override suspend fun ownedVenues(): List<VenueOption> = emptyList()
            },
            repository,
            ReplyToReviewUseCase(repository),
        )
        advanceUntilIdle()
        assertEquals(UiState.Empty, vm.uiState.value.reviews)
    }

    @Test
    fun `khong tai duoc co so - Error co thu lai`() = runTest {
        venuesError = RuntimeException("offline")
        val vm = viewModel()
        advanceUntilIdle()
        val error = vm.uiState.value.reviews as UiState.Error
        assertTrue(error.retry != null)

        venuesError = null
        error.retry!!.invoke()
        advanceUntilIdle()
        assertEquals(listOf("a1", "a2", "c1"), vm.ids())
    }
}

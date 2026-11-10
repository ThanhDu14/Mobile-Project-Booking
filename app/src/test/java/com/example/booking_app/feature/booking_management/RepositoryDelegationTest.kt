package com.example.booking_app.feature.booking_management

import com.example.booking_app.feature.booking_management.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** These tests verify delegation only, not server transactions, authorization or query semantics. */
class RepositoryDelegationTest {
    private class FakeRepository : BookingManagementRepository {
        val pages = mutableListOf<Triple<BookingTab, String?, Int>>()
        val cancellations = mutableListOf<CancelBookingRequest>()
        val groupCancellations = mutableListOf<CancelGroupRequest>()
        val invalidations = MutableSharedFlow<Unit>()
        val details = MutableSharedFlow<BookingDetail?>(replay = 1)
        val groups = MutableSharedFlow<RecurringBookingGroup?>(replay = 1)
        var observedTab: BookingTab? = null
        var observedBooking: String? = null
        var observedGroup: String? = null
        var result = CancellationResult(78_111, 33_000)
        override suspend fun getPage(tab: BookingTab, cursor: String?, pageSize: Int): BookingPage {
            pages += Triple(tab, cursor, pageSize)
            return BookingPage(emptyList(), "opaque-next")
        }
        override fun observeTabInvalidations(tab: BookingTab): Flow<Unit> { observedTab = tab; return invalidations }
        override fun observeBooking(bookingId: String): Flow<BookingDetail?> { observedBooking = bookingId; return details }
        override fun observeGroup(recurringGroupId: String): Flow<RecurringBookingGroup?> { observedGroup = recurringGroupId; return groups }
        override suspend fun estimateQuote(bookingId: String): RefundPolicyQuote = error("Cancellation must not request a quote")
        override suspend fun cancelBooking(request: CancelBookingRequest): CancellationResult { cancellations += request; return result }
        override suspend fun cancelRemainingGroup(request: CancelGroupRequest): CancellationResult { groupCancellations += request; return result }
    }

    @Test fun cancellationForwardsRetryAndReturnsServerResult() = runBlocking {
        val repo = FakeRepository()
        val useCase = CancelBookingUseCase(repo)
        val request = CancelBookingRequest("session", "reason", "request-1")
        assertEquals(repo.result, useCase(request))
        repo.result = CancellationResult(0, 111_111)
        assertEquals(repo.result, useCase(request))
        val other = request.copy(requestId = "request-2")
        useCase(other)
        assertEquals(listOf(request, request, other), repo.cancellations)
    }

    @Test fun groupCancellationDelegatesSingleSessionAndRemainingGroupSeparately() = runBlocking {
        val repo = FakeRepository()
        val session = CancelBookingRequest("session-2", "reason", "id-1")
        val group = CancelGroupRequest("group", "reason", "id-2")
        assertEquals(repo.result, CancelBookingUseCase(repo)(session))
        assertEquals(repo.result, CancelRemainingGroupUseCase(repo)(group))
        assertEquals(listOf(session), repo.cancellations)
        assertEquals(listOf(group), repo.groupCancellations)
    }

    @Test fun pagesAndInvalidationsAreDelegatedForEveryTabAndOpaqueCursor() = runBlocking {
        val repo = FakeRepository()
        val pages = GetBookingPageUseCase(repo)
        val invalidations = ObserveBookingTabInvalidationsUseCase(repo)
        for (tab in BookingTab.values()) {
            assertEquals("opaque-next", pages(tab, null, 10).nextCursor)
            pages(tab, "opaque-next", 10)
            assertSame(repo.invalidations, invalidations(tab))
            assertEquals(tab, repo.observedTab)
        }
        assertEquals(BookingTab.values().size * 2, repo.pages.size)
        for (tab in BookingTab.values()) {
            assertTrue(repo.pages.contains(Triple(tab, null, 10)))
            assertTrue(repo.pages.contains(Triple(tab, "opaque-next", 10)))
        }
    }

    @Test fun detailAndGroupStreamsArePassedThrough() = runBlocking {
        val repo = FakeRepository()
        val detailStream = ObserveBookingUseCase(repo)("booking")
        val groupStream = ObserveBookingGroupUseCase(repo)("group")
        assertSame(repo.details, detailStream)
        assertSame(repo.groups, groupStream)
        assertEquals("booking", repo.observedBooking)
        assertEquals("group", repo.observedGroup)
        repo.details.emit(null)
        val group = RecurringBookingGroup("group", emptyList())
        repo.groups.emit(group)
        assertNull(detailStream.first())
        assertEquals(group, groupStream.first())
    }
}

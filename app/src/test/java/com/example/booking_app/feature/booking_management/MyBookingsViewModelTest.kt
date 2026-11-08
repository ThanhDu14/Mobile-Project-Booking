package com.example.booking_app.feature.booking_management

import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.booking_management.domain.*
import com.example.booking_app.feature.booking_management.ui.MyBookingsState
import com.example.booking_app.feature.booking_management.ui.MyBookingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyBookingsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private class FakeRepo : BookingManagementRepository {
        var pageHandler: (BookingTab, String?, Int) -> BookingPage = { tab, cursor, _ ->
            BookingPage(emptyList(), null)
        }
        val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var lastObservedTab: BookingTab? = null
        var countsFlow: Flow<Map<BookingTab, Int>> = kotlinx.coroutines.flow.flowOf(emptyMap())

        override fun observeTabCounts(): Flow<Map<BookingTab, Int>> = countsFlow

        override suspend fun getPage(tab: BookingTab, cursor: String?, pageSize: Int): BookingPage {
            return pageHandler(tab, cursor, pageSize)
        }

        override fun observeTabInvalidations(tab: BookingTab): Flow<Unit> {
            lastObservedTab = tab
            return invalidations
        }

        override fun observeBooking(bookingId: String): Flow<BookingDetail?> = error("Not used")
        override fun observeGroup(recurringGroupId: String): Flow<RecurringBookingGroup?> = error("Not used")
        override suspend fun estimateQuote(bookingId: String): RefundPolicyQuote = error("Not used")
        override suspend fun cancelBooking(request: CancelBookingRequest): CancellationResult = error("Not used")
        override suspend fun cancelRemainingGroup(request: CancelGroupRequest): CancellationResult = error("Not used")
    }

    private fun fakeBooking(id: String) = BookingDetail(
        bookingId = id,
        userId = "u1",
        venueId = "v1",
        venueSnapshot = VenueSnapshot("Sân Test", "Địa chỉ test", "0900000000"),
        items = emptyList(),
        startAtMillis = 1000L,
        endAtMillis = 2000L,
        status = RefundPolicy.Status.CONFIRMED,
        finance = BookingFinance(
            subtotal = 50_000L,
            discount = 0L,
            total = 50_000L,
            depositAmount = 0L,
            paidAmount = 50_000L,
            remainingAmount = 0L,
            paymentMethod = RefundPolicy.PaymentMethod.MOCK_WALLET,
            paymentStatus = PaymentStatus.PAID
        ),
        createdAtMillis = 100L,
        updatedAtMillis = 100L
    )

    @Test
    fun `selectTab loads page and emits Content state`() = runTest {
        val repo = FakeRepo()
        repo.pageHandler = { tab, cursor, _ ->
            BookingPage(listOf(fakeBooking("b1"), fakeBooking("b2")), "cursor-1")
        }
        val vm = MyBookingsViewModel(repo)
        vm.selectTab(BookingTab.UPCOMING)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is MyBookingsState.Content)
        val content = state as MyBookingsState.Content
        assertEquals(2, content.bookings.size)
        assertEquals("cursor-1", content.nextCursor)
    }

    @Test
    fun `empty page emits Empty state`() = runTest {
        val repo = FakeRepo()
        repo.pageHandler = { _, _, _ -> BookingPage(emptyList(), null) }
        val vm = MyBookingsViewModel(repo)
        vm.selectTab(BookingTab.PENDING)
        advanceUntilIdle()

        assertTrue(vm.state.value is MyBookingsState.Empty)
    }

    @Test
    fun `loadNextPage appends items when cursor present`() = runTest {
        val repo = FakeRepo()
        repo.pageHandler = { tab, cursor, _ ->
            if (cursor == null) {
                BookingPage(listOf(fakeBooking("b1")), "cursor-1")
            } else {
                BookingPage(listOf(fakeBooking("b2")), null)
            }
        }
        val vm = MyBookingsViewModel(repo)
        vm.selectTab(BookingTab.UPCOMING)
        advanceUntilIdle()

        var content = vm.state.value as MyBookingsState.Content
        assertEquals(1, content.bookings.size)
        assertEquals("cursor-1", content.nextCursor)

        vm.loadNextPage()
        advanceUntilIdle()

        content = vm.state.value as MyBookingsState.Content
        assertEquals(2, content.bookings.size)
        assertNull(content.nextCursor)
    }

    @Test
    fun `unsupported repository emits Error state with unsupported flag`() = runTest {
        val repo = FakeRepo()
        repo.pageHandler = { _, _, _ -> throw UnsupportedOperationException("Not supported") }
        val vm = MyBookingsViewModel(repo)
        vm.selectTab(BookingTab.HOLD)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is MyBookingsState.Error)
        assertTrue((state as MyBookingsState.Error).unsupported)
    }

    @Test
    fun `general exception emits Error state with generic message`() = runTest {
        val repo = FakeRepo()
        repo.pageHandler = { _, _, _ -> throw RuntimeException("Network disconnected") }
        val vm = MyBookingsViewModel(repo)
        vm.selectTab(BookingTab.COMPLETED)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is MyBookingsState.Error)
        assertFalse((state as MyBookingsState.Error).unsupported)
    }

    @Test
    fun `invalidation triggers page reload for active tab`() = runTest {
        val repo = FakeRepo()
        var callCount = 0
        repo.pageHandler = { _, _, _ ->
            callCount++
            BookingPage(listOf(fakeBooking("b$callCount")), null)
        }
        val vm = MyBookingsViewModel(repo)
        vm.selectTab(BookingTab.UPCOMING)
        advanceUntilIdle()
        assertEquals(1, callCount)

        repo.invalidations.tryEmit(Unit)
        advanceUntilIdle()
        assertEquals(2, callCount)
    }

    @Test
    fun `tabCounts emits counts from repository`() = runTest {
        val repo = FakeRepo().apply {
            countsFlow = kotlinx.coroutines.flow.flowOf(
                mapOf(BookingTab.HOLD to 1, BookingTab.PENDING to 2, BookingTab.UPCOMING to 3)
            )
        }
        val vm = MyBookingsViewModel(repo)
        advanceUntilIdle()

        val counts = vm.tabCounts.value
        assertEquals(1, counts[BookingTab.HOLD])
        assertEquals(2, counts[BookingTab.PENDING])
        assertEquals(3, counts[BookingTab.UPCOMING])
    }
}

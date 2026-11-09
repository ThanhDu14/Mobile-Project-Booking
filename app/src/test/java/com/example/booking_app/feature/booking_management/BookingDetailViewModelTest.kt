package com.example.booking_app.feature.booking_management

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.booking_management.domain.*
import com.example.booking_app.feature.booking_management.ui.BookingDetailState
import com.example.booking_app.feature.booking_management.ui.BookingDetailViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingDetailViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private class FakeRepo(private val bookingFlow: Flow<BookingDetail?>) : BookingManagementRepository {
        override fun observeBooking(bookingId: String): Flow<BookingDetail?> = bookingFlow
        override fun observeGroup(recurringGroupId: String): Flow<RecurringBookingGroup?> = error("Not used")
        override fun observeTabInvalidations(tab: BookingTab): Flow<Unit> = error("Not used")
        override suspend fun getPage(tab: BookingTab, cursor: String?, pageSize: Int): BookingPage = error("Not used")
        override suspend fun estimateQuote(bookingId: String): RefundPolicyQuote = error("Not used")
        override suspend fun cancelBooking(request: CancelBookingRequest): CancellationResult = error("Not used")
        override suspend fun cancelRemainingGroup(request: CancelGroupRequest): CancellationResult = error("Not used")
    }

    private fun sampleBooking(
        id: String = "b1",
        status: RefundPolicy.Status = RefundPolicy.Status.CONFIRMED,
        qrToken: String? = "sample-token",
        checkedInAtMillis: Long? = null
    ) = BookingDetail(
        bookingId = id,
        userId = "u1",
        venueId = "v1",
        venueSnapshot = VenueSnapshot("Sân Cầu Lông Đào Duy Anh", "123 Đào Duy Anh", "0901234567"),
        items = listOf(
            BookingSlotItem("s1", "c1", "Sân 1", 1000L, 2000L, 100_000L)
        ),
        startAtMillis = 1000L,
        endAtMillis = 2000L,
        status = status,
        finance = BookingFinance(
            subtotal = 100_000L,
            discount = 0L,
            total = 100_000L,
            depositAmount = 0L,
            paidAmount = 100_000L,
            remainingAmount = 0L,
            paymentMethod = RefundPolicy.PaymentMethod.MOCK_WALLET,
            paymentStatus = PaymentStatus.PAID
        ),
        qrToken = qrToken,
        checkedInAtMillis = checkedInAtMillis,
        createdAtMillis = 500L,
        updatedAtMillis = 500L
    )

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `empty bookingId emits NotFound immediately`() = runTest {
        val repo = FakeRepo(flowOf(null))
        val vm = BookingDetailViewModel(SavedStateHandle(), repo, GenerateQrCodeUseCase())
        advanceUntilIdle()

        assertTrue(vm.state.value is BookingDetailState.NotFound)
    }

    @Test
    fun `booking not found emits NotFound`() = runTest {
        val repo = FakeRepo(flowOf(null))
        val handle = SavedStateHandle(mapOf("bookingId" to "b1"))
        val vm = BookingDetailViewModel(handle, repo, GenerateQrCodeUseCase())
        advanceUntilIdle()

        assertTrue(vm.state.value is BookingDetailState.NotFound)
    }

    @Test
    fun `confirmed booking without check-in generates QR bitmap`() = runTest {
        mockkStatic(Bitmap::class)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { Bitmap.createBitmap(any<Int>(), any<Int>(), any<Bitmap.Config>()) } returns mockBitmap

        val repo = FakeRepo(flowOf(sampleBooking(status = RefundPolicy.Status.CONFIRMED, qrToken = "qr-123")))
        val handle = SavedStateHandle(mapOf("bookingId" to "b1"))
        val vm = BookingDetailViewModel(handle, repo, GenerateQrCodeUseCase())
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is BookingDetailState.Content)
        val content = state as BookingDetailState.Content
        assertNotNull(content.qrBitmap)
        assertEquals("b1", content.booking.bookingId)
    }

    @Test
    fun `confirmed booking with null qrToken falls back to bookingId and generates QR bitmap`() = runTest {
        mockkStatic(Bitmap::class)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { Bitmap.createBitmap(any<Int>(), any<Int>(), any<Bitmap.Config>()) } returns mockBitmap

        val repo = FakeRepo(flowOf(sampleBooking(status = RefundPolicy.Status.CONFIRMED, qrToken = null)))
        val handle = SavedStateHandle(mapOf("bookingId" to "b1"))
        val vm = BookingDetailViewModel(handle, repo, GenerateQrCodeUseCase())
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is BookingDetailState.Content)
        val content = state as BookingDetailState.Content
        assertNotNull(content.qrBitmap)
        assertEquals("b1", content.booking.bookingId)
    }

    @Test
    fun `checked-in booking does not generate QR bitmap`() = runTest {
        val repo = FakeRepo(flowOf(sampleBooking(status = RefundPolicy.Status.CONFIRMED, checkedInAtMillis = 1500L)))
        val handle = SavedStateHandle(mapOf("bookingId" to "b1"))
        val vm = BookingDetailViewModel(handle, repo, GenerateQrCodeUseCase())
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is BookingDetailState.Content)
        val content = state as BookingDetailState.Content
        assertNull(content.qrBitmap)
    }

    @Test
    fun `pending booking does not generate QR bitmap`() = runTest {
        val repo = FakeRepo(flowOf(sampleBooking(status = RefundPolicy.Status.PENDING)))
        val handle = SavedStateHandle(mapOf("bookingId" to "b1"))
        val vm = BookingDetailViewModel(handle, repo, GenerateQrCodeUseCase())
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is BookingDetailState.Content)
        val content = state as BookingDetailState.Content
        assertNull(content.qrBitmap)
    }
}

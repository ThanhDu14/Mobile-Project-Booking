package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.payment.domain.HoldClock
import com.example.booking_app.feature.payment.domain.model.BookingPaymentState
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.PaymentStatus
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import com.example.booking_app.feature.payment.domain.usecase.ObserveBookingPaymentUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Màn chuyển khoản VietQR (spec 050 US4, T040). */
class TransferViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val messages = XmlPaymentMessages()
    private val states = MutableSharedFlow<BookingPaymentState>()

    private val receipt = PaymentReceipt(
        bookingId = "req-1", recurringGroupId = null, venueName = "Sân Ánh Dương", items = listOf("Sân 1"),
        option = PaymentOption.TRANSFER_DEPOSIT, status = PaymentStatus.DEPOSITED, subtotal = 120_000, discount = 0,
        total = 120_000, paidAmount = 36_000, remainingAmount = 84_000, voucherCode = null,
    )

    private inner class FakeRepo : PaymentRepository {
        val observed = mutableListOf<Pair<String, Boolean>>()
        override suspend fun confirmAtVenue(req: ConfirmRequest) = Result.failure<PaymentReceipt>(UnsupportedOperationException())
        override suspend fun receipt(bookingId: String, isRecurring: Boolean) = Result.failure<PaymentReceipt>(UnsupportedOperationException())
        override suspend fun startTransfer(req: ConfirmRequest, option: PaymentOption) =
            Result.failure<TransferInstruction>(UnsupportedOperationException())
        override fun observe(bookingId: String, isRecurring: Boolean): Flow<BookingPaymentState> {
            observed += bookingId to isRecurring
            return states
        }
    }

    private val repo = FakeRepo()

    /** Hạn giữ chỗ 90 giây sau thời điểm bắt đầu test (giờ ảo của scheduler). */
    private fun TestScope.viewModel(expiresAt: Long = 90_000L): TransferViewModel = TransferViewModel(
        SavedStateHandle(
            mapOf(
                "bookingId" to "req-1",
                "isRecurring" to false,
                "paymentCode" to "SNPAAAA2222",
                "amountDue" to 36_000L,
                "holdExpiresAtMillis" to expiresAt,
                "qrUrl" to "https://qr.sepay.vn/img?acc=0123456789&bank=970422&amount=36000&des=SNPAAAA2222",
                "bankShortName" to "MBBank",
                "accountNumber" to "0123456789",
                "accountHolderName" to "NGUYEN VAN A",
            ),
        ),
        ObserveBookingPaymentUseCase(repo),
        HoldClock { testScheduler.currentTime },
        messages,
    )

    @Test
    fun `hien thong tin chuyen khoan tu args va lang nghe dung don`() = runTest {
        val vm = viewModel()
        runCurrent()

        val instruction = vm.uiState.value.instruction!!
        assertEquals("SNPAAAA2222", instruction.paymentCode)
        assertEquals(36_000L, instruction.amountDue)
        assertEquals("MBBank", instruction.payoutAccount.bankShortName)
        assertEquals("NGUYEN VAN A", instruction.payoutAccount.accountHolderName)
        assertEquals(listOf("req-1" to false), repo.observed)
        assertEquals(90L, vm.uiState.value.holdRemainingSeconds)
    }

    @Test
    fun `dong ho dem theo holdExpiresAtMillis cua server, cham 0 thi expired`() = runTest {
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(60L, vm.uiState.value.holdRemainingSeconds)
        assertFalse(vm.uiState.value.expired)

        vm.effects.test {
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(PaymentEffect.ShowMessage(messages.string(PaymentMessages.Key.HOLD_EXPIRED)), awaitItem())
        }
        assertEquals(0L, vm.uiState.value.holdRemainingSeconds)
        assertTrue(vm.uiState.value.expired)
    }

    @Test
    fun `listener bao Confirmed thi mo hoa don`() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.effects.test {
            states.emit(BookingPaymentState.Confirmed(receipt))
            assertEquals(PaymentEffect.OpenReceipt(receipt), awaitItem())
        }
    }

    @Test
    fun `listener bao Expired thi khoa man va bao het han`() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.effects.test {
            states.emit(BookingPaymentState.Expired)
            assertEquals(PaymentEffect.ShowMessage(messages.string(PaymentMessages.Key.HOLD_EXPIRED)), awaitItem())
        }
        assertTrue(vm.uiState.value.expired)
    }

    @Test
    fun `tien ve trong luc an han van mo hoa don du dong ho da ve 0`() = runTest {
        val vm = viewModel(expiresAt = 1_000L)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(vm.uiState.value.expired)
        vm.effects.test {
            assertTrue(awaitItem() is PaymentEffect.ShowMessage)
            states.emit(BookingPaymentState.Confirmed(receipt))
            assertEquals(PaymentEffect.OpenReceipt(receipt), awaitItem())
        }
    }
}

package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.navigation.HoldToPaymentArgs
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.payment.domain.HoldClock
import com.example.booking_app.feature.payment.domain.model.BookingPaymentState
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentError
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.PaymentStatus
import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import com.example.booking_app.feature.payment.domain.repository.VenuePayoutRepository
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.domain.usecase.ConfirmAtVenueUseCase
import com.example.booking_app.feature.payment.domain.usecase.PreviewVoucherUseCase
import com.example.booking_app.feature.payment.domain.usecase.StartTransferUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Chống bấm trùng và thử lại an toàn ở màn thanh toán (spec 050 US5, T056). */
class PaymentViewModelIdempotencyTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val now = 1_800_000_000_000L
    private val messages = XmlPaymentMessages()

    private val receipt = PaymentReceipt(
        bookingId = "req-1", recurringGroupId = null, venueName = "Sân A", items = emptyList(),
        option = PaymentOption.AT_VENUE, status = PaymentStatus.UNPAID, subtotal = 120_000, discount = 0,
        total = 120_000, paidAmount = 0, remainingAmount = 120_000, voucherCode = null,
    )

    private inner class SlowRepo : PaymentRepository {
        val confirmIds = mutableListOf<String>()
        val transferIds = mutableListOf<String>()
        val results = ArrayDeque<Result<PaymentReceipt>>()

        /** Server mất 2 giây mới trả lời. */
        override suspend fun confirmAtVenue(req: ConfirmRequest): Result<PaymentReceipt> {
            confirmIds += req.requestId
            delay(2_000)
            return results.removeFirstOrNull() ?: Result.success(receipt)
        }
        override suspend fun receipt(bookingId: String, isRecurring: Boolean) = Result.failure<PaymentReceipt>(IllegalStateException())
        override suspend fun startTransfer(req: ConfirmRequest, option: PaymentOption): Result<TransferInstruction> {
            transferIds += req.requestId
            delay(2_000)
            return Result.failure(PaymentError.Network())
        }
        override fun observe(bookingId: String, isRecurring: Boolean) = emptyFlow<BookingPaymentState>()
    }

    private val repo = SlowRepo()
    private val vouchers = object : VoucherRepository {
        override suspend fun available(venueId: String) = Result.success(emptyList<Voucher>())
        override suspend fun findByCode(venueId: String, code: String) = Result.success<Voucher?>(null)
    }

    private fun TestScope.viewModel(transfer: Boolean = false) = PaymentViewModel(
        SavedStateHandle(
            mapOf(
                PaymentViewModel.ARG_KEY to HoldToPaymentArgs(
                    bookingId = "req-1", venueId = "v1", venueName = "Sân A", subtotalAmount = 120_000,
                    slotIds = arrayOf("c1_20261124_1800"), holdExpiresAtMillis = now + 5 * 60_000,
                ),
            ),
        ),
        PreviewVoucherUseCase(vouchers, FakeClock { now }),
        vouchers,
        object : VenuePayoutRepository {
            override suspend fun payoutAccount(venueId: String) = Result.success<PayoutAccount?>(
                if (transfer) PayoutAccount("970422", "MBBank", "0123456789", "NGUYEN VAN A") else null,
            )
        },
        ConfirmAtVenueUseCase(repo),
        StartTransferUseCase(repo),
        HoldClock { now },
        messages,
    )

    @Test
    fun `5 lan bam trong 500 ms chi goi server mot lan`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        launch {
            repeat(5) {
                vm.confirm()
                delay(100)
            }
        }
        advanceTimeBy(500)
        assertTrue(vm.uiState.value.submitting)
        advanceUntilIdle()
        assertEquals(listOf("req-1"), repo.confirmIds)
    }

    @Test
    fun `mat mang thi bao kem nut Thu lai, thu lai dung cung requestId`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repo.results += Result.failure(PaymentError.Network())

        vm.effects.test {
            vm.confirm()
            val message = awaitItem() as PaymentEffect.ShowMessage
            assertEquals(messages.string(PaymentMessages.Key.CONFIRM_NETWORK), message.text)
            assertTrue(message.retryable)

            vm.confirm() // nút "Thử lại" trên Snackbar gọi lại confirm()
            assertEquals(PaymentEffect.OpenReceipt(receipt), awaitItem())
        }
        assertEquals(listOf("req-1", "req-1"), repo.confirmIds)
    }

    @Test
    fun `chuyen khoan mat mang roi thu lai cung requestId`() = runTest {
        val vm = viewModel(transfer = true)
        advanceUntilIdle()
        vm.select(PaymentOption.TRANSFER_FULL)

        vm.effects.test {
            vm.confirm()
            assertTrue((awaitItem() as PaymentEffect.ShowMessage).retryable)
            vm.confirm()
            assertTrue((awaitItem() as PaymentEffect.ShowMessage).retryable)
        }
        assertEquals(listOf("req-1", "req-1"), repo.transferIds)
    }
}

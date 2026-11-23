package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.navigation.HoldToPaymentArgs
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.payment.domain.PaymentCalculator
import com.example.booking_app.feature.payment.domain.VoucherRules
import com.example.booking_app.feature.payment.domain.model.BookingPaymentState
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentError
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.PaymentStatus
import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherScope
import com.example.booking_app.feature.payment.domain.model.VoucherType
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import com.example.booking_app.feature.payment.domain.repository.VenuePayoutRepository
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.domain.usecase.ConfirmAtVenueUseCase
import com.example.booking_app.feature.payment.domain.usecase.PreviewVoucherUseCase
import com.example.booking_app.feature.payment.domain.usecase.StartTransferUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Chọn cách trả và chốt đơn ở `PaymentViewModel` (spec 050 US2, T027). */
class PaymentViewModelConfirmTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val now = 1_800_000_000_000L
    private val messages = XmlPaymentMessages()

    private fun args(subtotal: Long = 200_000, recurring: Boolean = false) = HoldToPaymentArgs(
        bookingId = "req-1",
        venueId = "venue-1",
        venueName = "Sân Ánh Dương",
        isRecurring = recurring,
        totalSessions = if (recurring) 4 else 1,
        subtotalAmount = subtotal,
        slotIds = arrayOf("c1_20261124_1800", "c1_20261124_1830"),
        holdExpiresAtMillis = now + 5 * 60_000,
    )

    private val payout = PayoutAccount("970422", "MBBank", "0123456789", "NGUYEN VAN A")

    private val sale20 = Voucher(
        code = "SALE20", scope = VoucherScope.SYSTEM, type = VoucherType.PERCENT, value = 20, maxDiscount = 30_000,
    )

    private fun receipt(total: Long = 200_000) = PaymentReceipt(
        bookingId = "req-1", recurringGroupId = null, venueName = "Sân Ánh Dương", items = listOf("Sân 1"),
        option = PaymentOption.AT_VENUE, status = PaymentStatus.UNPAID, subtotal = total, discount = 0,
        total = total, paidAmount = 0, remainingAmount = total, voucherCode = null,
    )

    private class FakePaymentRepository : PaymentRepository {
        val requests = mutableListOf<ConfirmRequest>()
        var gate: CompletableDeferred<Unit>? = null
        val results = ArrayDeque<Result<PaymentReceipt>>()
        override suspend fun confirmAtVenue(req: ConfirmRequest): Result<PaymentReceipt> {
            requests += req
            gate?.await()
            return results.removeFirst()
        }
        override suspend fun receipt(bookingId: String, isRecurring: Boolean): Result<PaymentReceipt> =
            Result.failure(UnsupportedOperationException())

        val transfers = mutableListOf<Pair<ConfirmRequest, PaymentOption>>()
        val transferResults = ArrayDeque<Result<TransferInstruction>>()
        override suspend fun startTransfer(req: ConfirmRequest, option: PaymentOption): Result<TransferInstruction> {
            transfers += req to option
            return transferResults.removeFirst()
        }
        override fun observe(bookingId: String, isRecurring: Boolean): Flow<BookingPaymentState> = emptyFlow()
    }

    private class FakePayout(private val account: PayoutAccount?) : VenuePayoutRepository {
        override suspend fun payoutAccount(venueId: String): Result<PayoutAccount?> = Result.success(account)
    }

    private class FakeVouchers(private val all: List<Voucher>) : VoucherRepository {
        override suspend fun available(venueId: String) = Result.success(all)
        override suspend fun findByCode(venueId: String, code: String) = Result.success(VoucherRules.pickByCode(code, all))
    }

    private val payments = FakePaymentRepository()

    private fun TestScope.viewModel(
        holdArgs: HoldToPaymentArgs = args(),
        account: PayoutAccount? = payout,
    ): PaymentViewModel {
        val clock = FakeClock { now }
        val vouchers = FakeVouchers(listOf(sale20))
        return PaymentViewModel(
            SavedStateHandle(mapOf(PaymentViewModel.ARG_KEY to holdArgs)),
            PreviewVoucherUseCase(vouchers, clock),
            vouchers,
            FakePayout(account),
            ConfirmAtVenueUseCase(payments),
            StartTransferUseCase(payments),
            messages,
        )
    }

    @Test
    fun `co so chua co tai khoan nhan tien thi an chuyen khoan va khong cho chon`() = runTest {
        val vm = viewModel(account = null)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.transferSupported)
        vm.select(PaymentOption.TRANSFER_DEPOSIT)
        assertEquals(PaymentOption.AT_VENUE, vm.uiState.value.selected)
    }

    @Test
    fun `co tai khoan nhan tien thi chon duoc coc va tra du`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.transferSupported)
        vm.select(PaymentOption.TRANSFER_DEPOSIT)
        assertEquals(PaymentOption.TRANSFER_DEPOSIT, vm.uiState.value.selected)
        assertEquals(60_000L, vm.uiState.value.breakdown?.amountDue(PaymentOption.TRANSFER_DEPOSIT))
        assertEquals(140_000L, vm.uiState.value.breakdown?.remainingAfterDeposit)
    }

    @Test
    fun `bam xac nhan 5 lan chi goi ConfirmAtVenueUseCase mot lan`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        payments.gate = CompletableDeferred()
        payments.results += Result.success(receipt())

        repeat(5) { vm.confirm() }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.submitting)
        payments.gate!!.complete(Unit)
        advanceUntilIdle()
        repeat(2) { vm.confirm() }
        advanceUntilIdle()

        assertEquals(1, payments.requests.size)
    }

    @Test
    fun `gui dung requestId, tong tien va voucher dang ap, don dinh ky gui recurringGroupId`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.applyVoucher(sale20)
        payments.results += Result.success(receipt())

        vm.confirm()
        advanceUntilIdle()

        val req = payments.requests.single()
        assertEquals("req-1", req.requestId)
        assertNull(req.recurringGroupId)
        assertEquals("venue-1", req.venueId)
        assertEquals(listOf("c1_20261124_1800", "c1_20261124_1830"), req.slotIds)
        assertEquals("SALE20", req.voucherCode)
        assertEquals(170_000L, req.expectedTotal)

        val recurring = viewModel(holdArgs = args(recurring = true))
        advanceUntilIdle()
        payments.results += Result.success(receipt())
        recurring.confirm()
        advanceUntilIdle()
        assertEquals("req-1", payments.requests.last().recurringGroupId)
    }

    @Test
    fun `thanh cong phat OpenReceipt`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        payments.results += Result.success(receipt())

        vm.effects.test {
            vm.confirm()
            assertEquals(PaymentEffect.OpenReceipt(receipt()), awaitItem())
        }
        assertFalse(vm.uiState.value.submitting)
    }

    @Test
    fun `loi mang roi thu lai van dung requestId cu`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        payments.results += Result.failure(PaymentError.Network())
        payments.results += Result.success(receipt())

        vm.effects.test {
            vm.confirm()
            assertEquals(PaymentEffect.ShowMessage(messages.string(PaymentMessages.Key.CONFIRM_NETWORK)), awaitItem())
            vm.confirm()
            assertTrue(awaitItem() is PaymentEffect.OpenReceipt)
        }
        assertEquals(listOf("req-1", "req-1"), payments.requests.map { it.requestId })
    }

    @Test
    fun `PriceChanged cap nhat lai tien va bao, khong mo hoa don`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val newBreakdown = PaymentCalculator.breakdown(240_000, discount = 0L)
        payments.results += Result.failure(PaymentError.PriceChanged(newBreakdown))

        vm.effects.test {
            vm.confirm()
            assertEquals(PaymentEffect.ShowMessage(messages.string(PaymentMessages.Key.PRICE_CHANGED)), awaitItem())
        }
        assertEquals(newBreakdown, vm.uiState.value.breakdown)
        assertFalse(vm.uiState.value.submitting)
    }

    @Test
    fun `server bao voucher het luot thi go voucher, ve gia goc va hien loi o o voucher`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.applyVoucher(sale20)
        payments.results += Result.failure(PaymentError.VoucherInvalid("OUT_OF_USES"))

        vm.confirm()
        advanceUntilIdle()

        assertNull(vm.uiState.value.voucher)
        assertEquals(200_000L, vm.uiState.value.breakdown?.total)
        assertEquals("Mã khuyến mãi đã hết hạn hoặc hết lượt sử dụng", vm.uiState.value.voucherError)
    }

    @Test
    fun `het han giu cho thi expired va khoa nut`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        payments.results += Result.failure(PaymentError.HoldExpired())

        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.expired)

        vm.confirm()
        advanceUntilIdle()
        assertEquals(1, payments.requests.size)
    }

    @Test
    fun `tong 0d thi chot tai san du dang chon chuyen khoan`() = runTest {
        val free = Voucher(code = "FREE", scope = VoucherScope.SYSTEM, type = VoucherType.FIXED, value = 500_000)
        val vm = viewModel()
        advanceUntilIdle()
        vm.select(PaymentOption.TRANSFER_FULL)
        vm.applyVoucher(free)
        payments.results += Result.success(receipt(total = 0))

        vm.confirm()
        advanceUntilIdle()

        assertEquals(0L, payments.requests.single().expectedTotal)
        assertEquals(PaymentOption.AT_VENUE, vm.uiState.value.selected)
    }

    private val instruction = TransferInstruction(
        bookingId = "req-1", paymentCode = "SNPAAAA2222", amountDue = 60_000, holdExpiresAtMillis = now + 15 * 60_000,
        qrUrl = "https://qr.sepay.vn/img?acc=0123456789&bank=970422&amount=60000&des=SNPAAAA2222", payoutAccount = payout,
    )

    @Test
    fun `chon coc 30 phan tram thi goi StartTransfer cung requestId va mo man chuyen khoan`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.select(PaymentOption.TRANSFER_DEPOSIT)
        payments.transferResults += Result.success(instruction)

        vm.effects.test {
            vm.confirm()
            assertEquals(PaymentEffect.OpenTransfer(instruction, isRecurring = false), awaitItem())
        }
        val (req, option) = payments.transfers.single()
        assertEquals(PaymentOption.TRANSFER_DEPOSIT, option)
        assertEquals("req-1", req.requestId)
        assertEquals(200_000L, req.expectedTotal)
        assertTrue(payments.requests.isEmpty())
        assertFalse(vm.uiState.value.submitting)
    }

    @Test
    fun `quay lai tu man chuyen khoan va doi sang tai san thi chot cung requestId`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.select(PaymentOption.TRANSFER_FULL)
        payments.transferResults += Result.success(instruction)
        vm.confirm()
        advanceUntilIdle()

        vm.select(PaymentOption.AT_VENUE)
        payments.results += Result.success(receipt())
        vm.confirm()
        advanceUntilIdle()

        assertEquals("req-1", payments.requests.single().requestId)
    }

    @Test
    fun `server bao co so khong nhan chuyen khoan thi an lua chon va ve tai san`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.select(PaymentOption.TRANSFER_FULL)
        payments.transferResults += Result.failure(PaymentError.TransferNotSupported())

        vm.effects.test {
            vm.confirm()
            assertEquals(PaymentEffect.ShowMessage(messages.string(PaymentMessages.Key.TRANSFER_UNSUPPORTED)), awaitItem())
        }
        assertFalse(vm.uiState.value.transferSupported)
        assertEquals(PaymentOption.AT_VENUE, vm.uiState.value.selected)
    }
}

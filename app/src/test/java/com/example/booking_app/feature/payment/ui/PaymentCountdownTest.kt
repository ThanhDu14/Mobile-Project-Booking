package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.navigation.HoldToPaymentArgs
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.payment.domain.HoldClock
import com.example.booking_app.feature.payment.domain.model.BookingPaymentState
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import com.example.booking_app.feature.payment.domain.repository.VenuePayoutRepository
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.domain.usecase.ConfirmAtVenueUseCase
import com.example.booking_app.feature.payment.domain.usecase.PreviewVoucherUseCase
import com.example.booking_app.feature.payment.domain.usecase.StartTransferUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Đồng hồ giữ chỗ ở màn thanh toán (spec 050 US3, T052). */
class PaymentCountdownTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val messages = XmlPaymentMessages()

    private val payments = object : PaymentRepository {
        var calls = 0
        override suspend fun confirmAtVenue(req: ConfirmRequest): Result<PaymentReceipt> {
            calls++
            return Result.failure(IllegalStateException())
        }
        override suspend fun receipt(bookingId: String, isRecurring: Boolean) = Result.failure<PaymentReceipt>(IllegalStateException())
        override suspend fun startTransfer(req: ConfirmRequest, option: PaymentOption) =
            Result.failure<TransferInstruction>(IllegalStateException())
        override fun observe(bookingId: String, isRecurring: Boolean) = emptyFlow<BookingPaymentState>()
    }

    private val vouchers = object : VoucherRepository {
        override suspend fun available(venueId: String) = Result.success(emptyList<Voucher>())
        override suspend fun findByCode(venueId: String, code: String) = Result.success<Voucher?>(null)
    }

    /** Giờ server = giờ ảo của scheduler; hạn giữ chỗ do 040 cấp nằm ở `holdExpiresAtMillis`. */
    private fun TestScope.viewModel(expiresAt: Long) = PaymentViewModel(
        SavedStateHandle(
            mapOf(
                PaymentViewModel.ARG_KEY to HoldToPaymentArgs(
                    bookingId = "req-1", venueId = "v1", venueName = "Sân A", subtotalAmount = 120_000,
                    slotIds = arrayOf("c1_20261124_1800"), holdExpiresAtMillis = expiresAt,
                ),
            ),
        ),
        PreviewVoucherUseCase(vouchers, FakeClock { testScheduler.currentTime }),
        vouchers,
        object : VenuePayoutRepository {
            override suspend fun payoutAccount(venueId: String) = Result.success<PayoutAccount?>(null)
        },
        ConfirmAtVenueUseCase(payments),
        StartTransferUseCase(payments),
        HoldClock { testScheduler.currentTime },
        messages,
    )

    @Test
    fun `dem theo holdExpiresAtMillis cua 040, khong tao 5 phut moi`() = runTest {
        val vm = viewModel(expiresAt = 100_000L)
        backgroundScope.launch { vm.countdown.collect {} }
        runCurrent()
        assertEquals(100L, vm.uiState.value.holdRemainingSeconds)

        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(60L, vm.countdown.value)
        assertEquals(60L, vm.uiState.value.holdRemainingSeconds)
        assertFalse(vm.uiState.value.expired)
    }

    @Test
    fun `cham 0 thi expired, bao het han va khong goi server nua`() = runTest {
        val vm = viewModel(expiresAt = 3_000L)
        backgroundScope.launch { vm.countdown.collect {} }
        vm.effects.test {
            advanceTimeBy(3_001)
            runCurrent()
            assertEquals(PaymentEffect.ShowMessage(messages.string(PaymentMessages.Key.HOLD_EXPIRED)), awaitItem())
        }
        assertTrue(vm.uiState.value.expired)
        assertEquals(0L, vm.uiState.value.holdRemainingSeconds)

        vm.confirm()
        runCurrent()
        assertEquals(0, payments.calls)
    }

    @Test
    fun `mo man khi da qua han thi expired ngay`() = runTest {
        advanceTimeBy(10_000)
        val vm = viewModel(expiresAt = 5_000L)
        backgroundScope.launch { vm.countdown.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.expired)
    }
}

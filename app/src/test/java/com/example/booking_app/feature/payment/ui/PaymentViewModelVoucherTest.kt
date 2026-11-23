package com.example.booking_app.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.navigation.HoldToPaymentArgs
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.payment.domain.VoucherRules
import com.example.booking_app.feature.payment.domain.model.AppliedVoucher
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherScope
import com.example.booking_app.feature.payment.domain.model.VoucherType
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import com.example.booking_app.feature.payment.domain.repository.VenuePayoutRepository
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.domain.usecase.ConfirmAtVenueUseCase
import com.example.booking_app.feature.payment.domain.usecase.PreviewVoucherUseCase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Phần tóm tắt + voucher của `PaymentViewModel` (spec 050 US1, T019).
 * Câu báo lỗi đọc từ `strings_payment.xml` thật qua [XmlPaymentMessages], nên test kiểm đúng câu trong spec.
 */
class PaymentViewModelVoucherTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun args(subtotal: Long = 200_000) = HoldToPaymentArgs(
        bookingId = "req-1",
        venueId = "venue-1",
        venueName = "Sân Cầu Lông Ánh Dương",
        venueAddress = "12 Nguyễn Trãi",
        districtName = "Quận 1",
        subtotalAmount = subtotal,
        slotIds = arrayOf("c1_20261124_1800", "c1_20261124_1830"),
        holdExpiresAtMillis = now + 5 * 60_000,
        courtDetails = arrayOf("Sân 1 · 18:00 - 19:00"),
    )

    private fun voucher(
        code: String,
        type: VoucherType = VoucherType.PERCENT,
        value: Long = 20,
        maxDiscount: Long? = 30_000,
        minOrderTotal: Long = 0,
        validTo: Long? = now + day,
        usageLimit: Long? = 100,
        usedCount: Long = 0,
    ) = Voucher(
        code = code, scope = VoucherScope.SYSTEM, type = type, value = value, maxDiscount = maxDiscount,
        minOrderTotal = minOrderTotal, validFromMillis = now - day, validToMillis = validTo,
        usageLimit = usageLimit, usedCount = usedCount,
    )

    private val vouchers = listOf(
        voucher("SALE20"),
        voucher("MIN200", minOrderTotal = 200_000),
        voucher("OLD", validTo = now - 1),
        voucher("HETLUOT", usageLimit = 10, usedCount = 10),
        voucher("FREE100", type = VoucherType.FIXED, value = 100_000, maxDiscount = null),
    )

    private class FakeRepo(private val all: List<Voucher>) : VoucherRepository {
        var availableCalls = 0
        override suspend fun available(venueId: String): Result<List<Voucher>> {
            availableCalls++
            return Result.success(all)
        }
        override suspend fun findByCode(venueId: String, code: String): Result<Voucher?> =
            Result.success(VoucherRules.pickByCode(code, all))
    }

    private val messages = XmlPaymentMessages()

    private fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(mapOf(PaymentViewModel.ARG_KEY to args())),
        repo: VoucherRepository = FakeRepo(vouchers),
    ): PaymentViewModel {
        val clock = FakeClock { now }
        return PaymentViewModel(
            handle,
            PreviewVoucherUseCase(repo, clock),
            repo,
            object : VenuePayoutRepository {
                override suspend fun payoutAccount(venueId: String) = Result.success<PayoutAccount?>(null)
            },
            ConfirmAtVenueUseCase(object : PaymentRepository {
                override suspend fun confirmAtVenue(req: ConfirmRequest) = Result.failure<PaymentReceipt>(IllegalStateException())
                override suspend fun receipt(bookingId: String, isRecurring: Boolean) =
                    Result.failure<PaymentReceipt>(IllegalStateException())
            }),
            messages,
        )
    }

    @Test
    fun `mo man thi hien tom tat don va gia goc, requestId lay tu bookingId`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals("req-1", vm.requestId)
        assertEquals("Sân Cầu Lông Ánh Dương", state.summary?.venueName)
        assertEquals(listOf("Sân 1 · 18:00 - 19:00"), state.summary?.items)
        assertEquals(200_000L, state.breakdown?.subtotal)
        assertEquals(0L, state.breakdown?.discount)
        assertEquals(200_000L, state.breakdown?.total)
        assertNull(state.voucher)
    }

    @Test
    fun `thieu args thi bao loi thay vi treo o loading`() = runTest {
        val vm = viewModel(handle = SavedStateHandle())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.loading)
        assertEquals(messages.missingOrder(), vm.uiState.value.error)
    }

    @Test
    fun `ap ma hop le cap nhat breakdown, go ma ve gia goc`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.uiState.test {
            skipItems(1)
            vm.applyCode("sale20")
            assertTrue(awaitItem().applyingVoucher)
            val applied = awaitItem()
            assertFalse(applied.applyingVoucher)
            assertEquals(AppliedVoucher("SALE20", VoucherScope.SYSTEM, 30_000), applied.voucher)
            assertEquals(30_000L, applied.breakdown?.discount)
            assertEquals(170_000L, applied.breakdown?.total)
            assertEquals(51_000L, applied.breakdown?.depositAmount)
            assertNull(applied.voucherError)

            vm.removeVoucher()
            val removed = awaitItem()
            assertNull(removed.voucher)
            assertEquals(0L, removed.breakdown?.discount)
            assertEquals(200_000L, removed.breakdown?.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chua dat don toi thieu thi bao dung cau spec va giu gia goc`() = runTest {
        val vm = viewModel(handle = SavedStateHandle(mapOf(PaymentViewModel.ARG_KEY to args(subtotal = 160_000))))
        advanceUntilIdle()

        vm.applyCode("MIN200")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Đơn hàng chưa đạt giá trị tối thiểu 200.000đ", state.voucherError)
        assertNull(state.voucher)
        assertEquals(160_000L, state.breakdown?.total)
    }

    @Test
    fun `het han hoac het luot cung mot cau bao`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.applyCode("OLD")
        advanceUntilIdle()
        assertEquals("Mã khuyến mãi đã hết hạn hoặc hết lượt sử dụng", vm.uiState.value.voucherError)

        vm.applyCode("HETLUOT")
        advanceUntilIdle()
        assertEquals("Mã khuyến mãi đã hết hạn hoặc hết lượt sử dụng", vm.uiState.value.voucherError)
        assertEquals(200_000L, vm.uiState.value.breakdown?.total)
    }

    @Test
    fun `ma loi khong go voucher dang ap`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.applyCode("SALE20")
        advanceUntilIdle()

        vm.applyCode("OLD")
        advanceUntilIdle()

        assertEquals("SALE20", vm.uiState.value.voucher?.code)
        assertEquals(170_000L, vm.uiState.value.breakdown?.total)
        assertTrue(vm.uiState.value.voucherError != null)
    }

    @Test
    fun `ma khong ton tai va ma trong`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.applyCode("KHONGCO")
        advanceUntilIdle()
        assertEquals(messages.notFound(), vm.uiState.value.voucherError)

        vm.applyCode("   ")
        advanceUntilIdle()
        assertEquals(messages.emptyCode(), vm.uiState.value.voucherError)
    }

    @Test
    fun `go ma xoa loi cu`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.applyCode("OLD")
        advanceUntilIdle()

        vm.removeVoucher()

        assertNull(vm.uiState.value.voucherError)
    }

    @Test
    fun `tong 0 dong thi isFree`() = runTest {
        val vm = viewModel(handle = SavedStateHandle(mapOf(PaymentViewModel.ARG_KEY to args(subtotal = 80_000))))
        advanceUntilIdle()

        vm.applyCode("FREE100")
        advanceUntilIdle()

        val breakdown = vm.uiState.value.breakdown!!
        assertEquals(80_000L, breakdown.discount)
        assertEquals(0L, breakdown.total)
        assertTrue(breakdown.isFree)
    }

    @Test
    fun `chon tu danh sach ap luon khong doc lai repository`() = runTest {
        val repo = FakeRepo(vouchers)
        val vm = viewModel(repo = repo)
        advanceUntilIdle()

        vm.loadVouchers()
        advanceUntilIdle()
        assertEquals(vouchers, vm.uiState.value.availableVouchers)

        vm.applyVoucher(vouchers.first())
        assertEquals(170_000L, vm.uiState.value.breakdown?.total)

        vm.loadVouchers()
        advanceUntilIdle()
        assertEquals(1, repo.availableCalls)
    }

    @Test
    fun `loi mang khi kiem ma bao loi, giu gia goc`() = runTest {
        val failing = object : VoucherRepository {
            override suspend fun available(venueId: String) = Result.failure<List<Voucher>>(IllegalStateException())
            override suspend fun findByCode(venueId: String, code: String) =
                Result.failure<Voucher?>(IllegalStateException("offline"))
        }
        val vm = viewModel(repo = failing)
        advanceUntilIdle()

        vm.applyCode("SALE20")
        advanceUntilIdle()

        assertEquals(messages.checkFailed(), vm.uiState.value.voucherError)
        assertFalse(vm.uiState.value.applyingVoucher)
        assertEquals(200_000L, vm.uiState.value.breakdown?.total)
    }
}

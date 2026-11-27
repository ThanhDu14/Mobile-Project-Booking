package com.example.booking_app.feature.payment.domain

import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.payment.domain.model.AppliedVoucher
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherCheck
import com.example.booking_app.feature.payment.domain.model.VoucherScope
import com.example.booking_app.feature.payment.domain.model.VoucherType
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.domain.usecase.PreviewVoucherUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Xem trước voucher ở app (spec 050 US1, contracts/ui-contracts.md §3). Server kiểm lại khi chốt. */
class PreviewVoucherUseCaseTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun voucher(
        code: String = "SALE20",
        scope: VoucherScope = VoucherScope.SYSTEM,
        type: VoucherType = VoucherType.PERCENT,
        value: Long = 20,
        maxDiscount: Long? = 30_000,
        minOrderTotal: Long = 0,
        validFrom: Long? = now - day,
        validTo: Long? = now + day,
        usageLimit: Long? = 100,
        usedCount: Long = 0,
        isActive: Boolean = true,
    ) = Voucher(
        code = code, scope = scope, type = type, value = value, maxDiscount = maxDiscount,
        minOrderTotal = minOrderTotal, validFromMillis = validFrom, validToMillis = validTo,
        usageLimit = usageLimit, usedCount = usedCount, isActive = isActive,
    )

    private class FakeVoucherRepository(var venue: List<Voucher> = emptyList(), var system: List<Voucher> = emptyList()) :
        VoucherRepository {
        val requestedCodes = mutableListOf<String>()
        override suspend fun available(venueId: String): Result<List<Voucher>> =
            Result.success(VoucherRules.mergeAvailable(venue, system))

        override suspend fun findByCode(venueId: String, code: String): Result<Voucher?> {
            requestedCodes += code
            return Result.success(VoucherRules.pickByCode(code, venue + system))
        }
    }

    private fun useCase(repo: VoucherRepository = FakeVoucherRepository()) = PreviewVoucherUseCase(repo, FakeClock { now })

    @Test
    fun `ma hop le tra Valid voi so tien giam theo PaymentCalculator`() {
        val result = useCase().check(voucher(), subtotal = 200_000)
        assertEquals(VoucherCheck.Valid(AppliedVoucher("SALE20", VoucherScope.SYSTEM, 30_000)), result)
    }

    @Test
    fun `voucher tat thi Expired`() {
        assertEquals(VoucherCheck.Expired, useCase().check(voucher(isActive = false), 200_000))
    }

    @Test
    fun `chua toi validFrom hoac qua validTo thi Expired`() {
        assertEquals(VoucherCheck.Expired, useCase().check(voucher(validFrom = now + 1), 200_000))
        assertEquals(VoucherCheck.Expired, useCase().check(voucher(validTo = now - 1), 200_000))
    }

    @Test
    fun `dung moc validFrom va validTo van hop le`() {
        assertTrue(useCase().check(voucher(validFrom = now), 200_000) is VoucherCheck.Valid)
        assertTrue(useCase().check(voucher(validTo = now), 200_000) is VoucherCheck.Valid)
    }

    @Test
    fun `tam tinh duoi don toi thieu thi MinOrderNotMet kem nguong`() {
        assertEquals(
            VoucherCheck.MinOrderNotMet(300_000),
            useCase().check(voucher(minOrderTotal = 300_000), 200_000),
        )
    }

    @Test
    fun `het luot thi OutOfUses`() {
        assertEquals(VoucherCheck.OutOfUses, useCase().check(voucher(usageLimit = 5, usedCount = 5), 200_000))
    }

    @Test
    fun `khong gioi han luot thi bo qua usedCount`() {
        assertTrue(useCase().check(voucher(usageLimit = null, usedCount = 999), 200_000) is VoucherCheck.Valid)
    }

    @Test
    fun `thu tu ly do giong server - het han truoc don toi thieu, don toi thieu truoc het luot`() {
        val bad = voucher(isActive = false, minOrderTotal = 300_000, usageLimit = 1, usedCount = 1)
        assertEquals(VoucherCheck.Expired, useCase().check(bad, 200_000))
        assertEquals(
            VoucherCheck.MinOrderNotMet(300_000),
            useCase().check(bad.copy(isActive = true), 200_000),
        )
    }

    @Test
    fun `khong co voucher thi NotFound`() = runTest {
        assertEquals(Result.success(VoucherCheck.NotFound), useCase().invoke("v1", "KHONGCO", 200_000))
    }

    @Test
    fun `ma nhap duoc chuan hoa trim va viet hoa truoc khi tim`() = runTest {
        val repo = FakeVoucherRepository(system = listOf(voucher()))
        val result = useCase(repo).invoke("v1", "  sale20 ", 200_000)
        assertEquals(listOf("SALE20"), repo.requestedCodes)
        assertTrue(result.getOrThrow() is VoucherCheck.Valid)
    }

    @Test
    fun `voucher co so uu tien hon he thong khi trung ma`() = runTest {
        val venue = voucher(scope = VoucherScope.VENUE, type = VoucherType.FIXED, value = 50_000, maxDiscount = null)
        val repo = FakeVoucherRepository(venue = listOf(venue), system = listOf(voucher()))

        val result = useCase(repo).invoke("v1", "SALE20", 200_000).getOrThrow()

        assertEquals(VoucherCheck.Valid(AppliedVoucher("SALE20", VoucherScope.VENUE, 50_000)), result)
        assertEquals(listOf(venue), repo.available("v1").getOrThrow())
    }

    @Test
    fun `loi doc repository tra ve Result failure`() = runTest {
        val failing = object : VoucherRepository {
            override suspend fun available(venueId: String) = Result.failure<List<Voucher>>(IllegalStateException())
            override suspend fun findByCode(venueId: String, code: String) =
                Result.failure<Voucher?>(IllegalStateException("offline"))
        }
        assertTrue(useCase(failing).invoke("v1", "SALE20", 200_000).isFailure)
    }
}

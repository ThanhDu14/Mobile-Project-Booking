package com.example.booking_app.feature.payment.data

import com.example.booking_app.feature.payment.domain.model.VoucherScope
import com.example.booking_app.feature.payment.domain.model.VoucherType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đọc voucher giống `toLoadedVoucher` ở `_shared/payment-voucher.ts`. */
class VoucherDocumentMapperTest {

    @Test
    fun `du lieu admin - PERCENTAGE, 0 la khong gioi han, ngay ISO co mui gio`() {
        val v = VoucherDocumentMapper.map(
            "SALE20", VoucherScope.SYSTEM,
            mapOf(
                "type" to "PERCENTAGE", "value" to 20L, "maxDiscount" to 0L, "minOrderTotal" to 100_000L,
                "validFrom" to "2026-11-01T00:00:00+07:00", "validTo" to "2026-11-30T23:59:59.000Z",
                "usageLimit" to 0L, "perUserLimit" to 0L, "usedCount" to 3L,
            ),
        )
        assertEquals(VoucherType.PERCENT, v.type)
        assertEquals(20L, v.value)
        assertNull(v.maxDiscount)
        assertNull(v.usageLimit)
        assertNull(v.perUserLimit)
        assertEquals(100_000L, v.minOrderTotal)
        assertEquals(1_793_466_000_000L, v.validFromMillis)
        assertEquals(1_796_083_199_000L, v.validToMillis)
        assertTrue(v.isActive)
    }

    @Test
    fun `FIXED, isActive false, ngay khong gio tinh 00 00 UTC nhu Date parse`() {
        val v = VoucherDocumentMapper.map(
            "GIAM50K", VoucherScope.VENUE,
            mapOf("type" to "fixed", "value" to 50_000.0, "isActive" to false, "validTo" to "2026-11-30", "validFrom" to ""),
        )
        assertEquals(VoucherType.FIXED, v.type)
        assertEquals(50_000L, v.value)
        assertFalse(v.isActive)
        assertNull(v.validFromMillis)
        assertEquals(1_795_996_800_000L, v.validToMillis)
    }
}

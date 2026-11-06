package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SystemVoucherValidationTest {

    @Test
    fun validVoucher_returnsNullError() {
        val voucher = AdminSystemVoucher(
            code = "TET2026",
            type = "PERCENTAGE",
            value = 20,
            maxDiscount = 50000,
            minOrderTotal = 100000,
            validFrom = "2026-11-01T00:00:00Z",
            validTo = "2026-11-30T23:59:59Z",
            usageLimit = 100,
            perUserLimit = 2,
        )
        assertNull(voucher.validate())
    }

    @Test
    fun invalidCode_tooShortOrInvalidCharacters_returnsError() {
        val voucherShort = AdminSystemVoucher(
            code = "AB",
            type = "FIXED",
            value = 20000,
            validFrom = "2026-11-01T00:00:00Z",
            validTo = "2026-11-30T23:59:59Z",
            usageLimit = 10,
            perUserLimit = 1,
        )
        assertEquals("Mã voucher phải từ 3 đến 20 ký tự chữ hoa hoặc số", voucherShort.validate())

        val voucherSpecial = voucherShort.copy(code = "SALE@2026")
        assertEquals("Mã voucher phải từ 3 đến 20 ký tự chữ hoa hoặc số", voucherSpecial.validate())
    }

    @Test
    fun invalidType_returnsError() {
        val voucher = AdminSystemVoucher(
            code = "SALE50",
            type = "INVALID",
            value = 50,
            validFrom = "2026-11-01T00:00:00Z",
            validTo = "2026-11-30T23:59:59Z",
            usageLimit = 10,
            perUserLimit = 1,
        )
        assertEquals("Loại voucher phải là PERCENTAGE hoặc FIXED", voucher.validate())
    }

    @Test
    fun invalidPercentageValue_greaterThan100_returnsError() {
        val voucher = AdminSystemVoucher(
            code = "SALE150",
            type = "PERCENTAGE",
            value = 150,
            validFrom = "2026-11-01T00:00:00Z",
            validTo = "2026-11-30T23:59:59Z",
            usageLimit = 10,
            perUserLimit = 1,
        )
        assertEquals("Phần trăm giảm không được vượt quá 100%", voucher.validate())
    }

    @Test
    fun invalidLimits_perUserGreaterThanUsageLimit_returnsError() {
        val voucher = AdminSystemVoucher(
            code = "SALE10K",
            type = "FIXED",
            value = 10000,
            validFrom = "2026-11-01T00:00:00Z",
            validTo = "2026-11-30T23:59:59Z",
            usageLimit = 5,
            perUserLimit = 10,
        )
        assertEquals("Giới hạn sử dụng không hợp lệ", voucher.validate())
    }
}

package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
import org.junit.Assert.assertNotNull
import org.junit.Test

class AdminVoucherRepairTest {
    private val valid = AdminSystemVoucher("SYS10", "FIXED", 10, validFrom = "2026-11-01T00:00:00+07:00", validTo = "2026-12-01T00:00:00+07:00", usageLimit = 10, perUserLimit = 1)

    @Test fun invalidDatesAndAmounts_areRejected() {
        listOf(valid.copy(validFrom = "01/10"), valid.copy(validTo = "invalid"),
            valid.copy(validFrom = "2026-11-01T00:00:00"),
            valid.copy(validFrom = "2026-02-30T00:00:00Z"),
            valid.copy(validTo = valid.validFrom), valid.copy(minOrderTotal = -1),
            valid.copy(maxDiscount = -1)).forEach { assertNotNull(it.toString(), it.validate()) }
    }
}

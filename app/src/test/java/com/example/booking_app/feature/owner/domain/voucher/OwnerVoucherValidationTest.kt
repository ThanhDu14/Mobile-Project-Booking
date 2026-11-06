package com.example.booking_app.feature.owner.domain.voucher

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerVoucherValidationTest {
    @Test fun acceptsExistingSchemaFieldsAndDoesNotRequireAStoredVenueIdOrUsageCounter() {
        assertTrue(OwnerVoucherValidation.validate(draft()).isEmpty())
    }

    @Test fun rejectsBlankOrNonUppercaseCodeAndNonPositiveValue() {
        val errors = OwnerVoucherValidation.validate(draft(code = "spring", value = 0))
        assertEquals(2, errors.size)
    }

    @Test fun rejectsNegativeThresholdsInvalidDateRangeAndInvalidLimits() {
        val errors = OwnerVoucherValidation.validate(
            draft(
                maxDiscount = -1,
                minOrder = -1,
                validFrom = LocalDate.parse("2026-12-02"),
                validTo = LocalDate.parse("2026-12-01"),
                usageLimit = 0,
                perUserLimit = -2,
            ),
        )
        assertEquals(5, errors.size)
    }

    @Test fun rejectsPercentageValueOver100AndPerUserLimitExceedingUsageLimit() {
        val percentErrors = OwnerVoucherValidation.validate(draft(value = 101L))
        assertTrue(percentErrors.any { it.contains("100%") })

        val limitErrors = OwnerVoucherValidation.validate(draft(usageLimit = 5, perUserLimit = 10))
        assertTrue(limitErrors.any { it.contains("lớn hơn tổng giới hạn") })
    }

    private fun draft(
        code: String = "SPRING26",
        value: Long = 10,
        maxDiscount: Long = 20_000,
        minOrder: Long = 100_000,
        validFrom: LocalDate = LocalDate.parse("2026-12-01"),
        validTo: LocalDate = LocalDate.parse("2026-12-31"),
        usageLimit: Int? = 100,
        perUserLimit: Int? = 1,
    ) = OwnerVoucherDraft(
        code, OwnerVoucherType.PERCENT, value, maxDiscount, minOrder,
        validFrom, validTo, usageLimit, perUserLimit, isActive = true,
    )
}

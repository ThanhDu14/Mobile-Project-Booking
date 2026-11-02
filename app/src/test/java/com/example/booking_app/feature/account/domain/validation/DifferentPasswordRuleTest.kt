package com.example.booking_app.feature.account.domain.validation

import com.example.booking_app.feature.account.domain.model.AccountError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DifferentPasswordRuleTest {

    @Test
    fun `mat khau moi khac mat khau hien tai thi hop le`() {
        assertNull(DifferentPasswordRule.validate(current = "Matkhau123", new = "Matkhau456"))
    }

    @Test
    fun `mat khau moi bang mat khau hien tai thi loi SamePassword`() {
        assertEquals(AccountError.SamePassword, DifferentPasswordRule.validate(current = "Matkhau123", new = "Matkhau123"))
    }

    @Test
    fun `so sanh chinh xac, phan biet hoa thuong`() {
        assertNull(DifferentPasswordRule.validate(current = "Matkhau123", new = "matkhau123"))
        assertNull(DifferentPasswordRule.validate(current = "Matkhau123", new = "Matkhau123 "))
    }
}

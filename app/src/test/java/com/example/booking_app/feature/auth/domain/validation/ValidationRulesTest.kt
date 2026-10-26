package com.example.booking_app.feature.auth.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameRuleTest {
    @Test
    fun `ten 2 den 50 ky tu sau khi trim la hop le`() {
        assertEquals(Validated.Valid("An"), NameRule.validate("  An "))
        assertEquals(Validated.Valid("a".repeat(50)), NameRule.validate("a".repeat(50)))
    }

    @Test
    fun `ten 1 ky tu, 51 ky tu hoac chi khoang trang bi tu choi`() {
        for (input in listOf("A", "a".repeat(51), "     ", "")) {
            assertEquals(Validated.Invalid(ValidationError.NAME_LENGTH), NameRule.validate(input))
        }
    }
}

class EmailRuleTest {
    @Test
    fun `email hop le duoc trim va viet thuong`() {
        assertEquals(Validated.Valid("nam.le@email.com"), EmailRule.validate("  Nam.Le@Email.COM "))
    }

    @Test
    fun `email sai dinh dang bi tu choi`() {
        for (input in listOf("", "nam", "nam@", "@email.com", "nam@email", "nam le@email.com")) {
            assertEquals(input, Validated.Invalid(ValidationError.EMAIL_FORMAT), EmailRule.validate(input))
        }
    }

    @Test
    fun `ma loi khop ten chuoi trong strings_auth`() {
        assertEquals("auth_error_email_format", ValidationError.EMAIL_FORMAT.key)
    }
}

class PasswordRuleTest {
    @Test
    fun `hai dau tich cap nhat rieng`() {
        assertEquals(PasswordChecks(len8 = false, letterDigit = false), PasswordRule.check("abc"))
        assertEquals(PasswordChecks(len8 = true, letterDigit = false), PasswordRule.check("abcdefgh"))
        assertEquals(PasswordChecks(len8 = false, letterDigit = true), PasswordRule.check("abc123"))
        assertEquals(PasswordChecks(len8 = true, letterDigit = true), PasswordRule.check("abcd1234"))
    }

    @Test
    fun `mat khau du 8 ky tu co chu va so la hop le`() {
        assertEquals(Validated.Valid("matkhau1"), PasswordRule.validate("matkhau1"))
        assertEquals(Validated.Valid("mậtkhẩu9"), PasswordRule.validate("mậtkhẩu9"))
    }

    @Test
    fun `thieu chu, thieu so hoac qua ngan bi tu choi`() {
        for (input in listOf("12345678", "abcdefgh", "abc123", "")) {
            assertEquals(input, Validated.Invalid(ValidationError.PASSWORD_RULE), PasswordRule.validate(input))
        }
    }

    @Test
    fun `isValid chi dung khi du ca hai`() {
        assertTrue(PasswordChecks(len8 = true, letterDigit = true).isValid)
        assertFalse(PasswordChecks(len8 = true, letterDigit = false).isValid)
    }
}

class ConfirmPasswordRuleTest {
    @Test
    fun `nhap lai trung mat khau`() {
        assertEquals(Validated.Valid("matkhau1"), ConfirmPasswordRule.validate("matkhau1", "matkhau1"))
    }

    @Test
    fun `nhap lai khac mat khau`() {
        assertEquals(
            Validated.Invalid(ValidationError.PASSWORD_MISMATCH),
            ConfirmPasswordRule.validate("matkhau1", "matkhau2"),
        )
    }
}

class PhoneRuleTest {
    @Test
    fun `so dau 0 hoac +84 duoc chuan hoa thanh E164`() {
        assertEquals(Validated.Valid("+84912345678"), PhoneRule.validate("0912 345 678"))
        assertEquals(Validated.Valid("+84901234567"), PhoneRule.validate("+84 901 234 567"))
        for (prefix in listOf("3", "5", "7", "8", "9")) {
            assertEquals(Validated.Valid("+84${prefix}12345678"), PhoneRule.validate("0${prefix}12345678"))
        }
    }

    @Test
    fun `dau so 2, 4, 6 hoac sai do dai bi tu choi`() {
        for (input in listOf("0212345678", "0412345678", "0612345678", "091234567", "09123456789", "", "abc")) {
            assertEquals(input, Validated.Invalid(ValidationError.PHONE_FORMAT), PhoneRule.validate(input))
        }
    }
}

class OtpRuleTest {
    @Test
    fun `dung 6 chu so`() {
        assertEquals(Validated.Valid("482190"), OtpRule.validate("482190"))
    }

    @Test
    fun `khac 6 chu so bi tu choi`() {
        for (input in listOf("", "48219", "4821900", "48219a", "48 219")) {
            assertEquals(input, Validated.Invalid(ValidationError.OTP_FORMAT), OtpRule.validate(input))
        }
    }
}

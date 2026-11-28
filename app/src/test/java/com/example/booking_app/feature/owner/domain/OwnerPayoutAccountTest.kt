package com.example.booking_app.feature.owner.domain

import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tài khoản nhận tiền: cùng điều kiện với Firestore Rules `isValidPayoutAccount`. */
class OwnerPayoutAccountTest {

    @Test
    fun `hop le thi dung tai khoan da chuan hoa`() {
        val account = OwnerPayoutAccount.build("970422", " 0123456789 ", "  Nguyễn   Văn Đạt ")
        assertEquals(PayoutAccount("970422", "MBBank", "0123456789", "NGUYEN VAN DAT"), account)
    }

    @Test
    fun `chua chon ngan hang hoac BIN la thi loi bank`() {
        assertEquals(listOf("bank"), OwnerPayoutAccount.validate(null, "0123456789", "A").map { it.field })
        assertEquals(listOf("bank"), OwnerPayoutAccount.validate("999999", "0123456789", "A").map { it.field })
    }

    @Test
    fun `so tai khoan phai 6 den 19 chu so`() {
        listOf("12345", "12345678901234567890", "12345a789", "").forEach {
            assertEquals(it, listOf("accountNumber"), OwnerPayoutAccount.validate("970436", it, "A").map { e -> e.field })
        }
        assertTrue(OwnerPayoutAccount.validate("970436", "123456", "A").isEmpty())
        assertTrue(OwnerPayoutAccount.validate("970436", "1234567890123456789", "A").isEmpty())
    }

    @Test
    fun `ten chu tai khoan khong duoc trong va toi da 50 ky tu`() {
        assertEquals(listOf("accountHolderName"), OwnerPayoutAccount.validate("970436", "123456", "   ").map { it.field })
        assertEquals(listOf("accountHolderName"), OwnerPayoutAccount.validate("970436", "123456", "A".repeat(51)).map { it.field })
        assertNull(OwnerPayoutAccount.build("970436", "123456", ""))
    }

    @Test
    fun `moi ngan hang co BIN 6 so khong trung`() {
        val bins = OwnerPayoutAccount.banks.map { it.bin }
        assertTrue(bins.all { it.matches(Regex("^[0-9]{6}$")) })
        assertEquals(bins.size, bins.toSet().size)
    }
}

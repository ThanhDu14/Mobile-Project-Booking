package com.example.booking_app.feature.account.domain.validation

import com.example.booking_app.R
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.ui.accountErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T084: ô "Gõ XÓA để xác nhận" (FR-018), so sau `trim` + chữ hoa. */
class DeleteConfirmRuleTest {

    @Test
    fun `XOA co dau, co khoang trang, chu thuong deu hop le`() {
        listOf("XÓA", " XÓA ", "xóa").forEach { assertNull(it, DeleteConfirmRule.validate(it)) }
    }

    @Test
    fun `khong dau, rong thi loi account_error_delete_confirm`() {
        listOf("XOA", "xoa", "", "XÓA TÀI KHOẢN").forEach {
            val error = DeleteConfirmRule.validate(it)
            assertEquals(it, AccountError.DeleteConfirmMismatch, error)
            assertEquals(R.string.account_error_delete_confirm, accountErrorMessage(error!!))
        }
    }
}

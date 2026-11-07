package com.example.booking_app.feature.account.domain.validation

import com.example.booking_app.feature.account.domain.model.AccountError

/** Ô "Gõ XÓA để xác nhận" (FR-018): so sau `trim` + chữ hoa, giống server `delete-account`. */
object DeleteConfirmRule {
    const val WORD = "XÓA"

    /** `null` khi hợp lệ. */
    fun validate(input: String): AccountError? =
        if (input.trim().uppercase() == WORD) null else AccountError.DeleteConfirmMismatch
}

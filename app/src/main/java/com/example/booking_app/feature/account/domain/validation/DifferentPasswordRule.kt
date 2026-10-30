package com.example.booking_app.feature.account.domain.validation

import com.example.booking_app.feature.account.domain.model.AccountError

/** Mật khẩu mới phải khác mật khẩu hiện tại (US5-3, research R9). So chính xác, không trim, phân biệt hoa thường. */
object DifferentPasswordRule {
    /** `null` khi hợp lệ. */
    fun validate(current: String, new: String): AccountError? = if (new == current) AccountError.SamePassword else null
}

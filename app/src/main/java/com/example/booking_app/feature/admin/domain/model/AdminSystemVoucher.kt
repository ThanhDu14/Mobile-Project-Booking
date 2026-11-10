package com.example.booking_app.feature.admin.domain.model

import java.util.Locale

data class AdminSystemVoucher(
    val code: String,
    val type: String,
    val value: Long,
    val maxDiscount: Long = 0,
    val minOrderTotal: Long = 0,
    val validFrom: String,
    val validTo: String,
    val usageLimit: Long,
    val perUserLimit: Long,
    val usedCount: Long = 0,
    val isActive: Boolean = true,
) {
    fun validate(): String? {
        val upperCode = code.trim().uppercase(Locale.ROOT)
        if (!Regex("^[A-Z0-9]{3,20}$").matches(upperCode)) {
            return "Mã voucher phải từ 3 đến 20 ký tự chữ hoa hoặc số"
        }
        val upperType = type.trim().uppercase(Locale.ROOT)
        if (upperType != "PERCENTAGE" && upperType != "FIXED") {
            return "Loại voucher phải là PERCENTAGE hoặc FIXED"
        }
        if (value <= 0) {
            return "Giá trị giảm phải lớn hơn 0"
        }
        if (upperType == "PERCENTAGE" && value > 100) {
            return "Phần trăm giảm không được vượt quá 100%"
        }
        if (usageLimit <= 0 || perUserLimit <= 0 || perUserLimit > usageLimit) {
            return "Giới hạn sử dụng không hợp lệ"
        }
        return null
    }
}

package com.example.booking_app.feature.admin.domain.model

import java.util.Locale
import java.time.OffsetDateTime

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
        if (upperType !in setOf("PERCENT", "PERCENTAGE", "FIXED")) {
            return "Loại voucher phải là PERCENTAGE hoặc FIXED"
        }
        if (value <= 0) {
            return "Giá trị giảm phải lớn hơn 0"
        }
        if (upperType in setOf("PERCENT", "PERCENTAGE") && value > 100) {
            return "Phần trăm giảm không được vượt quá 100%"
        }
        if (usageLimit <= 0 || perUserLimit <= 0 || perUserLimit > usageLimit) {
            return "Giới hạn sử dụng không hợp lệ"
        }
        if (minOrderTotal < 0 || maxDiscount < 0) return "Số tiền không được âm"
        val from = runCatching { OffsetDateTime.parse(validFrom).toInstant() }.getOrNull()
        val to = runCatching { OffsetDateTime.parse(validTo).toInstant() }.getOrNull()
        if (from == null || to == null || !from.isBefore(to)) return "Ngày hiệu lực phải có năm, giờ và múi giờ hợp lệ"
        return null
    }
}

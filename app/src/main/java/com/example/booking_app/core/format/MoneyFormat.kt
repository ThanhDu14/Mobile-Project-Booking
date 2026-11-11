package com.example.booking_app.core.format

/** Tiền VND hiển thị dạng `80.000 đ`, không phụ thuộc locale của máy (FR-027). */
object MoneyFormat {

    /** `80000` → `80.000 đ`; số âm giữ dấu `-`. */
    fun vnd(amount: Long): String = "${grouped(amount)} đ"

    /** `80000` → `80.000` (không hậu tố), cho chuỗi tài nguyên tự thêm đơn vị. */
    fun grouped(amount: Long): String {
        val digits = if (amount < 0) amount.toString().drop(1) else amount.toString()
        val body = digits.reversed().chunked(3).joinToString(".").reversed()
        return if (amount < 0) "-$body" else body
    }
}

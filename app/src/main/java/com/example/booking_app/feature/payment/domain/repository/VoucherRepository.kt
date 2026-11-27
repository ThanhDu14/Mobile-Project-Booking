package com.example.booking_app.feature.payment.domain.repository

import com.example.booking_app.feature.payment.domain.model.Voucher

/** Đọc voucher để xem trước giá ở màn thanh toán (contracts/ui-contracts.md §2). Không ghi gì. */
interface VoucherRepository {
    /** Voucher cơ sở + hệ thống đang bật và còn hạn, cho danh sách chọn; trùng mã thì giữ voucher cơ sở. */
    suspend fun available(venueId: String): Result<List<Voucher>>

    /** Tìm theo mã đã chuẩn hóa: cơ sở trước, hệ thống sau. `null` = không có. */
    suspend fun findByCode(venueId: String, code: String): Result<Voucher?>
}

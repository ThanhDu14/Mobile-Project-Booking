package com.example.booking_app.feature.payment.domain.usecase

import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.payment.domain.VoucherRules
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherCheck
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import javax.inject.Inject

/**
 * Xem trước voucher ở app (contracts/ui-contracts.md §3). Chỉ đọc, không giữ lượt;
 * `booking-payment-confirm`/`booking-payment-transfer` kiểm lại trong transaction, kể cả số lượt.
 */
class PreviewVoucherUseCase @Inject constructor(
    private val repository: VoucherRepository,
    private val clock: AppClock,
) {
    /** Tìm theo mã người chơi nhập rồi kiểm. `Result.failure` khi không đọc được Firestore. */
    suspend operator fun invoke(venueId: String, rawCode: String, subtotal: Long): Result<VoucherCheck> =
        repository.findByCode(venueId, VoucherRules.normalize(rawCode)).map { check(it, subtotal) }

    /** Kiểm voucher đã có trong tay (chọn từ danh sách). */
    fun check(voucher: Voucher?, subtotal: Long): VoucherCheck = VoucherRules.check(voucher, subtotal, clock.now())
}

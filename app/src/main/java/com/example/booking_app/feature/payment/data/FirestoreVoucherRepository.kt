package com.example.booking_app.feature.payment.data

import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.payment.domain.VoucherRules
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherScope
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Đọc `venues/{venueId}/vouchers` và `systemVouchers` (Rules cho người đã đăng nhập đọc, research R7).
 * ID document là mã voucher. Không lọc `isActive`/hạn bằng query vì `validFrom`/`validTo` lưu dạng chuỗi
 * và dữ liệu cũ có thể thiếu `isActive`; số voucher mỗi nguồn nhỏ nên lọc ở app.
 */
class FirestoreVoucherRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val clock: AppClock,
) : VoucherRepository {

    override suspend fun available(venueId: String): Result<List<Voucher>> = runCatching {
        coroutineScope {
            val venue = async { venueVouchers(venueId).get().await().documents }
            val system = async { firestore.collection(SYSTEM).get().await().documents }
            val now = clock.now()
            VoucherRules.mergeAvailable(
                venue = venue.await().mapNotNull { it.toVoucher(VoucherScope.VENUE) }.filter { VoucherRules.isLive(it, now) },
                system = system.await().mapNotNull { it.toVoucher(VoucherScope.SYSTEM) }.filter { VoucherRules.isLive(it, now) },
            ).sortedWith(compareBy<Voucher> { it.scope }.thenBy { it.minOrderTotal }.thenBy { it.code })
        }
    }

    override suspend fun findByCode(venueId: String, code: String): Result<Voucher?> = runCatching {
        if (code.isEmpty()) return@runCatching null
        coroutineScope {
            val venue = async { venueVouchers(venueId).document(code).get().await() }
            val system = async { firestore.collection(SYSTEM).document(code).get().await() }
            VoucherRules.pickByCode(
                code,
                listOfNotNull(venue.await().toVoucher(VoucherScope.VENUE), system.await().toVoucher(VoucherScope.SYSTEM)),
            )
        }
    }

    private fun venueVouchers(venueId: String) =
        firestore.collection("venues").document(venueId).collection("vouchers")

    private fun DocumentSnapshot.toVoucher(scope: VoucherScope): Voucher? =
        data?.let { VoucherDocumentMapper.map(id, scope, it) }

    private companion object {
        const val SYSTEM = "systemVouchers"
    }
}

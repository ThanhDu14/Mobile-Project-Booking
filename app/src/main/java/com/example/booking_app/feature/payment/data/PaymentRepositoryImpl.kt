package com.example.booking_app.feature.payment.data

import com.example.booking_app.feature.payment.data.remote.PaymentFunctionsApi
import com.example.booking_app.feature.payment.domain.model.BookingPaymentState
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class PaymentRepositoryImpl @Inject constructor(
    private val api: PaymentFunctionsApi,
    private val receiptReader: FirestoreReceiptReader,
    private val firestore: FirebaseFirestore,
) : PaymentRepository {

    override suspend fun confirmAtVenue(req: ConfirmRequest): Result<PaymentReceipt> = safe { api.confirm(req) }

    override suspend fun receipt(bookingId: String, isRecurring: Boolean): Result<PaymentReceipt> =
        safe { receiptReader.read(bookingId, isRecurring) }

    override suspend fun startTransfer(req: ConfirmRequest, option: PaymentOption): Result<TransferInstruction> =
        safe { api.startTransfer(req, option) }

    /**
     * Listener trên document đơn/nhóm; gỡ khi ngừng thu (`awaitClose`). Lỗi listener (mất quyền, mạng) thì đóng
     * flow im lặng: màn chuyển khoản vẫn còn đồng hồ, và hóa đơn đọc lại được ở 060.
     */
    override fun observe(bookingId: String, isRecurring: Boolean): Flow<BookingPaymentState> {
        val ref = firestore.collection(if (isRecurring) "recurringGroups" else "bookings").document(bookingId)
        return callbackFlow {
            val registration = ref.addSnapshotListener { snapshot, error ->
                if (error != null) close() else trySend(snapshot?.data)
            }
            awaitClose { registration.remove() }
        }
            .mapNotNull { PaymentStateMapper.kind(it, isRecurring) }
            .distinctUntilChanged()
            .mapNotNull { kind ->
                when (kind) {
                    is PaymentStateMapper.Kind.Awaiting -> BookingPaymentState.AwaitingTransfer(kind.holdExpiresAtMillis)
                    PaymentStateMapper.Kind.Expired -> BookingPaymentState.Expired
                    // Các buổi của nhóm được ghi cùng transaction với nhóm nên đọc lại là đủ.
                    PaymentStateMapper.Kind.Confirmed ->
                        receiptReader.runCatching { read(bookingId, isRecurring) }.getOrNull()
                            ?.let { BookingPaymentState.Confirmed(it) }
                }
            }
    }

    private inline fun <T> safe(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

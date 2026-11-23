package com.example.booking_app.feature.payment.data

import com.example.booking_app.feature.payment.data.remote.PaymentFunctionsApi
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class PaymentRepositoryImpl @Inject constructor(
    private val api: PaymentFunctionsApi,
    private val receiptReader: FirestoreReceiptReader,
) : PaymentRepository {

    override suspend fun confirmAtVenue(req: ConfirmRequest): Result<PaymentReceipt> = safe { api.confirm(req) }

    override suspend fun receipt(bookingId: String, isRecurring: Boolean): Result<PaymentReceipt> =
        safe { receiptReader.read(bookingId, isRecurring) }

    private inline fun <T> safe(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

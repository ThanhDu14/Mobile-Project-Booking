package com.example.booking_app.feature.payment.data.remote

import android.util.Log
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.network.parseFunctionErrorCode
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentBreakdown
import com.example.booking_app.feature.payment.domain.model.PaymentError
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.PaymentStatus
import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import javax.inject.Inject

/** Các Edge Function thanh toán spec 050 (contracts/edge-functions.md). Lỗi là [PaymentError]. */
interface PaymentFunctionsApi {
    /** `booking-payment-confirm` (§1). */
    suspend fun confirm(req: ConfirmRequest): PaymentReceipt

    /** `booking-payment-transfer` (§2). */
    suspend fun startTransfer(req: ConfirmRequest, option: PaymentOption): TransferInstruction

    /** `booking-payment-simulate`: giả lập đã chuyển khoản (chỉ khi server bật thanh toán giả lập). */
    suspend fun simulateTransfer(paymentCode: String): Unit = throw UnsupportedOperationException()
}

@Serializable
internal data class SimulateBody(val paymentCode: String)

@Serializable
internal data class TransferBody(
    val requestId: String,
    val recurringGroupId: String?,
    val venueId: String,
    val slotIds: List<String>,
    val voucherCode: String?,
    val option: String,
    val expectedTotal: Long,
)

@Serializable
internal data class PayoutDto(val bankShortName: String, val accountNumber: String, val accountHolderName: String)

@Serializable
internal data class TransferResponseDto(
    val bookingId: String,
    val recurringGroupId: String? = null,
    val paymentCode: String,
    val amountDue: Long,
    val holdExpiresAt: String,
    val qrUrl: String,
    val payoutAccount: PayoutDto,
)

@Serializable
internal data class ConfirmBody(
    val requestId: String,
    val recurringGroupId: String?,
    val venueId: String,
    val slotIds: List<String>,
    val voucherCode: String?,
    val expectedTotal: Long,
)

@Serializable
internal data class ReceiptDto(
    val bookingId: String,
    val recurringGroupId: String? = null,
    val venueName: String = "",
    val items: List<String> = emptyList(),
    val option: String = "AT_VENUE",
    val status: String = "UNPAID",
    val subtotal: Long = 0,
    val discount: Long = 0,
    val total: Long = 0,
    val paidAmount: Long = 0,
    val remainingAmount: Long = 0,
    val voucherCode: String? = null,
)

@Serializable
internal data class ConfirmResponseDto(val receipt: ReceiptDto)

@Serializable
internal data class BreakdownDto(
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val depositAmount: Long,
    val isFree: Boolean,
)

@Serializable
internal data class VoucherErrorDto(val reason: String = "NOT_FOUND", val minOrderTotal: Long = 0)

/** Phần thuần (không gọi mạng): đọc body thành công và ánh xạ lỗi HTTP sang [PaymentError]. */
object PaymentFunctionResponses {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseConfirm(body: String): PaymentReceipt = json.decodeFromString<ConfirmResponseDto>(body).receipt.toDomain()

    fun parseTransfer(body: String): TransferInstruction = json.decodeFromString<TransferResponseDto>(body).let {
        TransferInstruction(
            bookingId = it.bookingId,
            paymentCode = it.paymentCode,
            amountDue = it.amountDue,
            holdExpiresAtMillis = Instant.parse(it.holdExpiresAt).toEpochMilli(),
            qrUrl = it.qrUrl,
            // `bankBin` chỉ server cần để dựng QR; app hiển thị tên ngân hàng và số tài khoản.
            payoutAccount = PayoutAccount("", it.payoutAccount.bankShortName, it.payoutAccount.accountNumber, it.payoutAccount.accountHolderName),
            recurringGroupId = it.recurringGroupId,
        )
    }

    fun mapFailure(error: Throwable): Throwable = when (error) {
        is PaymentError -> error
        AppError.Network, AppError.Timeout -> PaymentError.Network(error)
        is AppError.Unknown -> (error.cause as? FunctionHttpException)?.let(::mapHttp) ?: PaymentError.Unknown(error.message)
        // Khóa tài khoản / phiên hết: FunctionErrors đã báo SessionManager, để nguyên cho tầng trên.
        is AppError -> error
        else -> PaymentError.Unknown(error.message)
    }

    fun mapHttp(e: FunctionHttpException): PaymentError {
        val error = errorObject(e.body)
        return when (parseFunctionErrorCode(e.body)) {
            "HOLD_EXPIRED" -> PaymentError.HoldExpired()
            "PRICE_CHANGED" -> error?.get("breakdown")
                ?.let { PaymentError.PriceChanged(json.decodeFromJsonElement(BreakdownDto.serializer(), it).toDomain()) }
                ?: PaymentError.Unknown(e.message)
            "VOUCHER_INVALID" -> json.decodeFromJsonElement(VoucherErrorDto.serializer(), error ?: JsonObject(emptyMap()))
                .let { PaymentError.VoucherInvalid(it.reason, it.minOrderTotal) }
            "TRANSFER_NOT_SUPPORTED" -> PaymentError.TransferNotSupported()
            "NO_PRICE" -> PaymentError.NoPrice()
            else -> PaymentError.Unknown(e.message)
        }
    }

    private fun errorObject(body: String?): JsonObject? = runCatching {
        Json.parseToJsonElement(body!!).jsonObject["error"] as? JsonObject
    }.getOrNull()

    private fun BreakdownDto.toDomain() = PaymentBreakdown(subtotal, discount, total, depositAmount, isFree)

    private fun ReceiptDto.toDomain() = PaymentReceipt(
        bookingId = bookingId,
        recurringGroupId = recurringGroupId,
        venueName = venueName,
        items = items,
        option = PaymentOption.entries.firstOrNull { it.name == option } ?: PaymentOption.AT_VENUE,
        status = PaymentStatus.entries.firstOrNull { it.name == status } ?: PaymentStatus.UNPAID,
        subtotal = subtotal,
        discount = discount,
        total = total,
        paidAmount = paidAmount,
        remainingAmount = remainingAmount,
        voucherCode = voucherCode,
    )
}

class SupabasePaymentFunctionsApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : PaymentFunctionsApi {

    override suspend fun confirm(req: ConfirmRequest): PaymentReceipt {
        val body = ConfirmBody(req.requestId, req.recurringGroupId, req.venueId, req.slotIds, req.voucherCode, req.expectedTotal)
        return PaymentFunctionResponses.parseConfirm(call(CONFIRM) { supabase.functions.invoke(CONFIRM, body = body) })
    }

    override suspend fun startTransfer(req: ConfirmRequest, option: PaymentOption): TransferInstruction {
        val body = TransferBody(
            req.requestId, req.recurringGroupId, req.venueId, req.slotIds, req.voucherCode, option.name, req.expectedTotal,
        )
        return PaymentFunctionResponses.parseTransfer(call(TRANSFER) { supabase.functions.invoke(TRANSFER, body = body) })
    }

    override suspend fun simulateTransfer(paymentCode: String) {
        call(SIMULATE) { supabase.functions.invoke(SIMULATE, body = SimulateBody(paymentCode)) }
    }

    /** Gọi function qua `FunctionErrors` (tự làm mới token, báo khóa tài khoản), ánh xạ lỗi sang [PaymentError]. */
    private suspend fun call(name: String, invoke: suspend () -> HttpResponse): String = try {
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(TIMEOUT_MS) { invoke() }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            response.bodyAsText()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw PaymentFunctionResponses.mapFailure(e).also { if (it is PaymentError.Unknown) Log.w(TAG, "$name thất bại", e) }
    }

    private companion object {
        const val TAG = "PaymentFunctions"
        const val CONFIRM = "booking-payment-confirm"
        const val TRANSFER = "booking-payment-transfer"
        const val SIMULATE = "booking-payment-simulate"
        /** Transaction ghi nhiều document, có thể cold start: rộng hơn mặc định 15 giây. */
        const val TIMEOUT_MS = 30_000L
    }
}

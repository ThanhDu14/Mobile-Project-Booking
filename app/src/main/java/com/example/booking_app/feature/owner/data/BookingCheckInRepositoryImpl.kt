package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInInput
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInRepository
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject

@Serializable
private data class CheckInWireRequest(
    val bookingId: String,
    val confirmPayment: Boolean,
)

class BookingCheckInRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : BookingCheckInRepository {

    override suspend fun checkIn(input: BookingCheckInInput): Result<BookingCheckInResult> = runCatching {
        val normalized = input.normalized()
        val code = normalized.credential

        // Server tự tra đơn theo mã đơn hoặc qrToken rồi kiểm tra đơn thuộc cơ sở của chủ sân
        // (Rules spec 112 T012 không cho client query qrToken trên toàn collection `bookings`).
        val targetBookingId = code

        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_NAME,
                body = CheckInWireRequest(
                    bookingId = targetBookingId,
                    confirmPayment = normalized.ownerConfirmedPaymentCollected,
                ),
            )
        }

        val jsonObj = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val bookingId = jsonObj["bookingId"]?.jsonPrimitive?.content ?: targetBookingId
        val checkedInAtStr = jsonObj["checkedInAt"]?.jsonPrimitive?.content
        val epochMillis = if (!checkedInAtStr.isNullOrBlank()) {
            try {
                Instant.parse(checkedInAtStr).toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        } else {
            System.currentTimeMillis()
        }
        val paymentStatus = jsonObj["paymentStatus"]?.jsonPrimitive?.content

        BookingCheckInResult(
            bookingId = bookingId,
            checkedInAtEpochMillis = epochMillis,
            paymentStatus = paymentStatus,
        )
    }

    companion object {
        const val FUNCTION_NAME = "checkin-booking"
    }
}

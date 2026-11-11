package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInInput
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInRepository
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInResult
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject

@Serializable
private data class CheckInWireRequest(
    val venueId: String = "",
    val bookingId: String,
    val confirmPayment: Boolean,
)

class BookingCheckInRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : BookingCheckInRepository {

    override suspend fun checkIn(input: BookingCheckInInput): Result<BookingCheckInResult> = runCatching {
        val normalized = input.normalized()
        val code = normalized.credential

        var targetBookingId = code
        var targetVenueId = ""

        try {
            val byToken = firestore.collection("bookings")
                .whereEqualTo("qrToken", code)
                .limit(1)
                .get()
                .await()

            if (!byToken.isEmpty) {
                val doc = byToken.documents.first()
                targetBookingId = doc.id
                targetVenueId = doc.getString("venueId").orEmpty()
            } else {
                val directDoc = firestore.collection("bookings")
                    .document(code)
                    .get()
                    .await()
                if (directDoc.exists()) {
                    targetBookingId = directDoc.id
                    targetVenueId = directDoc.getString("venueId").orEmpty()
                }
            }
        } catch (_: Exception) {
            // Ignore firestore lookup failure; pass raw credential to Edge Function
        }

        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_NAME,
                body = CheckInWireRequest(
                    venueId = targetVenueId,
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

package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.booking.OwnerBooking
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingCommand
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingDetail
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingRepository
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject

@Serializable
private data class ApproveBookingWire(val venueId: String, val bookingId: String)

@Serializable
private data class RejectBookingWire(val venueId: String, val bookingId: String, val reason: String = "Từ chối bởi chủ sân")

@Serializable
private data class CancelBookingWire(val venueId: String, val bookingId: String, val reason: String)

class OwnerBookingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerBookingRepository {

    override fun observeBookings(venueId: String): Flow<List<OwnerBooking>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("bookings")
            .whereEqualTo("venueId", venueId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    val bookings = snapshot.documents.mapNotNull { doc ->
                        val statusStr = doc.getString("status")?.uppercase() ?: return@mapNotNull null
                        val status = try {
                            OwnerBookingStatus.valueOf(statusStr)
                        } catch (_: Exception) {
                            return@mapNotNull null
                        }

                        // Filter out HOLD from confirmed/pending queue
                        val startTimestamp = doc.getTimestamp("startAt") ?: doc.getTimestamp("startTime")
                        val endTimestamp = doc.getTimestamp("endAt") ?: doc.getTimestamp("endTime")
                        val deadlineTimestamp = doc.getTimestamp("approvalDeadline")

                        val startInstant = startTimestamp?.toDate()?.toInstant() ?: Instant.now()
                        val endInstant = endTimestamp?.toDate()?.toInstant() ?: Instant.now()
                        val deadlineInstant = deadlineTimestamp?.toDate()?.toInstant()

                        OwnerBooking(
                            bookingId = doc.id,
                            venueId = venueId,
                            status = status,
                            startAt = startInstant,
                            endAt = endInstant,
                            approvalDeadline = deadlineInstant,
                            customerDisplayName = doc.getString("customerDisplayName") ?: doc.getString("userName"),
                            courtNames = doc.get("courtNames") as? List<String> ?: emptyList(),
                            paymentMethod = doc.getString("paymentMethod"),
                            paymentStatus = doc.getString("paymentStatus"),
                            displayAmountVnd = doc.getLong("total") ?: doc.getLong("totalAmount"),
                            depositAmountVnd = doc.getLong("depositAmount"),
                            checkedInAt = doc.getTimestamp("checkedInAt")?.toDate()?.toInstant(),
                        )
                    }
                    trySend(bookings)
                }
            }

        awaitClose { registration.remove() }
    }

    override suspend fun getBooking(venueId: String, bookingId: String): Result<OwnerBookingDetail> = runCatching {
        val doc = firestore.collection("bookings").document(bookingId).get().result
        val statusStr = doc.getString("status")?.uppercase() ?: "PENDING"
        val status = try { OwnerBookingStatus.valueOf(statusStr) } catch (_: Exception) { OwnerBookingStatus.PENDING }

        val startInstant = doc.getTimestamp("startAt")?.toDate()?.toInstant() ?: Instant.now()
        val endInstant = doc.getTimestamp("endAt")?.toDate()?.toInstant() ?: Instant.now()

        val booking = OwnerBooking(
            bookingId = doc.id,
            venueId = venueId,
            status = status,
            startAt = startInstant,
            endAt = endInstant,
            approvalDeadline = doc.getTimestamp("approvalDeadline")?.toDate()?.toInstant(),
            customerDisplayName = doc.getString("customerDisplayName"),
            courtNames = doc.get("courtNames") as? List<String> ?: emptyList(),
            paymentMethod = doc.getString("paymentMethod"),
            paymentStatus = doc.getString("paymentStatus"),
            displayAmountVnd = doc.getLong("total"),
            depositAmountVnd = doc.getLong("depositAmount"),
            checkedInAt = doc.getTimestamp("checkedInAt")?.toDate()?.toInstant(),
        )

        OwnerBookingDetail(
            booking = booking,
            slotCount = (doc.get("slotIds") as? List<*>)?.size ?: 1,
            subtotalVnd = doc.getLong("subtotal"),
            discountVnd = doc.getLong("discount"),
            refundVnd = doc.getLong("refundAmount"),
            cancelReason = doc.getString("cancelReason"),
        )
    }

    override suspend fun submit(command: OwnerBookingCommand): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            when (command) {
                is OwnerBookingCommand.Approve -> {
                    supabase.functions.invoke(
                        "owner-approve-booking",
                        body = ApproveBookingWire(venueId = command.venueId, bookingId = command.bookingId),
                    )
                }
                is OwnerBookingCommand.Reject -> {
                    supabase.functions.invoke(
                        "owner-reject-booking",
                        body = RejectBookingWire(venueId = command.venueId, bookingId = command.bookingId),
                    )
                }
                is OwnerBookingCommand.CancelConfirmed -> {
                    supabase.functions.invoke(
                        "owner-cancel-booking",
                        body = CancelBookingWire(venueId = command.venueId, bookingId = command.bookingId, reason = command.reason),
                    )
                }
            }
        }
    }
}

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
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject

@Serializable
private data class ApproveBookingWire(val venueId: String, val bookingId: String)

@Serializable
internal data class RejectBookingWire(val venueId: String, val bookingId: String, val reason: String)

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
                        val startTimestamp = ownerTimestamp(doc.get("startAt")) ?: ownerTimestamp(doc.get("startTime"))
                        val endTimestamp = ownerTimestamp(doc.get("endAt")) ?: ownerTimestamp(doc.get("endTime"))
                        val deadlineTimestamp = ownerTimestamp(doc.get("approvalDeadline"))

                        val startInstant = startTimestamp?.toDate()?.toInstant() ?: return@mapNotNull null
                        val endInstant = endTimestamp?.toDate()?.toInstant() ?: return@mapNotNull null
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
                            checkedInAt = ownerTimestamp(doc.get("checkedInAt"))?.toDate()?.toInstant(),
                        )
                    }
                    trySend(bookings)
                }
            }

        awaitClose { registration.remove() }
    }

    override suspend fun getBooking(venueId: String, bookingId: String): Result<OwnerBookingDetail> = runCatching {
        val doc = firestore.collection("bookings").document(bookingId).get().await()
        check(doc.exists() && doc.getString("venueId") == venueId) { "Không tìm thấy đơn đặt trong cơ sở" }
        val statusStr = doc.getString("status")?.uppercase() ?: error("Đơn thiếu trạng thái")
        val status = OwnerBookingStatus.valueOf(statusStr)

        val startInstant = ownerTimestamp(doc.get("startAt"))?.toDate()?.toInstant() ?: error("Đơn thiếu giờ bắt đầu")
        val endInstant = ownerTimestamp(doc.get("endAt"))?.toDate()?.toInstant() ?: error("Đơn thiếu giờ kết thúc")

        val booking = OwnerBooking(
            bookingId = doc.id,
            venueId = venueId,
            status = status,
            startAt = startInstant,
            endAt = endInstant,
            approvalDeadline = ownerTimestamp(doc.get("approvalDeadline"))?.toDate()?.toInstant(),
            customerDisplayName = doc.getString("customerDisplayName"),
            courtNames = doc.get("courtNames") as? List<String> ?: emptyList(),
            paymentMethod = doc.getString("paymentMethod"),
            paymentStatus = doc.getString("paymentStatus"),
            displayAmountVnd = doc.getLong("total"),
            depositAmountVnd = doc.getLong("depositAmount"),
            checkedInAt = ownerTimestamp(doc.get("checkedInAt"))?.toDate()?.toInstant(),
        )

        OwnerBookingDetail(
            booking = booking,
            slotCount = (doc.get("slotIds") as? List<*>)?.size ?: (doc.get("items") as? List<*>)?.size
                ?: error("Đơn thiếu thông tin khung giờ"),
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
                        body = RejectBookingWire(venueId = command.venueId, bookingId = command.bookingId, reason = command.reason.trim()),
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

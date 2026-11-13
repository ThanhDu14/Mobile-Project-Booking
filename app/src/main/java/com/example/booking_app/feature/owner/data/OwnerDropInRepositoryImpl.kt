package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInDraft
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRegistration
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRepository
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInSession
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject

@Serializable
private data class CreateDropInWireRequest(
    val venueId: String,
    val courtIds: List<String>,
    val date: String,
    val startTime: String,
    val endTime: String,
    val capacity: Int,
    val pricePerPerson: Long,
    val level: String,
    val note: String,
)

@Serializable
private data class UpdateDropInWireRequest(
    val venueId: String,
    val sessionId: String,
    val courtIds: List<String>,
    val date: String,
    val startTime: String,
    val endTime: String,
    val capacity: Int,
    val pricePerPerson: Long,
    val level: String,
    val note: String,
)

@Serializable
private data class CancelDropInWireRequest(
    val venueId: String,
    val sessionId: String,
    val reason: String,
)

@Serializable
private data class CheckInDropInWireRequest(
    val venueId: String,
    val sessionId: String,
    val credential: String,
)

class OwnerDropInRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerDropInRepository {

    override fun observeSessions(venueId: String): Flow<List<OwnerDropInSession>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("dropInSessions")
            .whereEqualTo("venueId", venueId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    val sessions = snapshot.documents.mapNotNull { doc ->
                        val startTimestamp = doc.getTimestamp("startAt")
                        val endTimestamp = doc.getTimestamp("endAt")

                        val startInstant = startTimestamp?.toDate()?.toInstant() ?: Instant.now()
                        val endInstant = endTimestamp?.toDate()?.toInstant() ?: Instant.now()

                        val courtIds = (doc.get("courtIds") as? List<String>)
                            ?: listOfNotNull(doc.getString("courtId"))

                        OwnerDropInSession(
                            sessionId = doc.id,
                            venueId = venueId,
                            courtIds = courtIds,
                            startAt = startInstant,
                            endAt = endInstant,
                            capacity = doc.getLong("capacity")?.toInt() ?: 0,
                            registeredCount = doc.getLong("registeredCount")?.toInt() ?: 0,
                            pricePerPersonVnd = doc.getLong("pricePerPerson") ?: 0L,
                            status = doc.getString("status") ?: "OPEN",
                        )
                    }
                    trySend(sessions)
                }
            }

        awaitClose { registration.remove() }
    }

    override suspend fun getRoster(venueId: String, sessionId: String): Result<List<OwnerDropInRegistration>> = runCatching {
        val snapshot = firestore.collection("dropInSessions").document(sessionId)
            .collection("registrations")
            .get()
            .await()

        snapshot.documents.map { doc ->
            OwnerDropInRegistration(
                registrationId = doc.id,
                displayName = doc.getString("displayName") ?: doc.getString("userName") ?: "Người chơi",
                partySize = doc.getLong("partySize")?.toInt() ?: 1,
                status = doc.getString("status") ?: "REGISTERED",
                amountVnd = doc.getLong("amount") ?: doc.getLong("paidAmount") ?: 0L,
            )
        }
    }

    override suspend fun createSession(draft: OwnerDropInDraft): Result<String> = runCatching {
        val courtId = draft.courtIds.firstOrNull() ?: ""
        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                "owner-create-session",
                body = CreateDropInWireRequest(
                    venueId = draft.venueId,
                    courtIds = draft.courtIds,
                    date = draft.date,
                    startTime = draft.startTime.replace(":", ""),
                    endTime = draft.endTime.replace(":", ""),
                    capacity = draft.capacity,
                    pricePerPerson = draft.pricePerPersonVnd,
                    level = draft.level,
                    note = draft.note,
                ),
            )
        }
        val jsonObj = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        jsonObj["sessionId"]?.jsonPrimitive?.content ?: "dropin_${courtId}_${draft.date}"
    }

    override suspend fun updateSession(venueId: String, sessionId: String, draft: OwnerDropInDraft): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                "owner-update-session",
                body = UpdateDropInWireRequest(
                    venueId = venueId,
                    sessionId = sessionId,
                    courtIds = draft.courtIds,
                    date = draft.date,
                    startTime = draft.startTime.replace(":", ""),
                    endTime = draft.endTime.replace(":", ""),
                    capacity = draft.capacity,
                    pricePerPerson = draft.pricePerPersonVnd,
                    level = draft.level,
                    note = draft.note,
                ),
            )
        }
        Unit
    }

    override suspend fun cancelSession(venueId: String, sessionId: String, reason: String): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                "owner-cancel-session",
                body = CancelDropInWireRequest(
                    venueId = venueId,
                    sessionId = sessionId,
                    reason = reason,
                ),
            )
        }
    }

    override suspend fun checkIn(venueId: String, sessionId: String, credential: String): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                "owner-checkin-dropin-registration",
                body = CheckInDropInWireRequest(
                    venueId = venueId,
                    sessionId = sessionId,
                    credential = credential,
                ),
            )
        }
    }
}

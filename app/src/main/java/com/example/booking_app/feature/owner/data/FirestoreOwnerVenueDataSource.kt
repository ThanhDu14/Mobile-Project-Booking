package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreOwnerVenueDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager
) {
    private val uid: String
        get() = sessionManager.currentUid() ?: error("Owner chưa đăng nhập")

    suspend fun getOwnedVenues(): List<OwnerVenue> {
        return firestore.collection(VENUES).whereEqualTo("ownerId", uid).get().await()
            .documents.map(::toVenue)
    }

    suspend fun getVenue(venueId: String): OwnerVenue {
        return firestore.collection(VENUES).document(venueId).get().await()
            .takeIf { it.exists() }?.let(::toVenue)
            ?: error("Không tìm thấy cơ sở")
    }

    suspend fun updateVenue(venueId: String, fields: VenueEditableFields) {
        firestore.collection(VENUES).document(venueId).update(fields.toMap()).await()
    }

    suspend fun hideVenue(venueId: String) {
        firestore.collection(VENUES).document(venueId).update("status", "HIDDEN").await()
    }

    suspend fun setAcceptsRecurring(venueId: String, accepts: Boolean) {
        firestore.collection(VENUES).document(venueId).update("acceptsRecurring", accepts).await()
    }

    suspend fun getCourts(venueId: String): List<OwnerCourt> {
        return firestore.collection(VENUES).document(venueId).collection(COURTS)
            .get().await().documents.map(::toCourt).sortedBy { it.sortOrder }
    }

    suspend fun createCourt(venueId: String, fields: CourtFields): String {
        val ref = firestore.collection(VENUES).document(venueId).collection(COURTS).document()
        ref.set(fields.toMap()).await()
        return ref.id
    }

    suspend fun getPriceRules(venueId: String): List<OwnerPriceRule> = firestore.collection(VENUES).document(venueId)
        .collection(PRICE_RULES).get().await().documents.map { doc ->
            OwnerPriceRule(doc.id, (doc.get("daysOfWeek") as? List<*>).orEmpty().mapNotNull { it as? Long }.map { it.toInt() }, doc.getString("startTime").orEmpty(), doc.getString("endTime").orEmpty(), doc.getLong("pricePerHour") ?: 0L, doc.getString("label").orEmpty())
        }

    suspend fun getSlots(venueId: String, date: String): List<OwnerSlot> = firestore.collection(SLOTS)
        .whereEqualTo("venueId", venueId).whereEqualTo("date", date).get().await().documents.map { doc ->
            OwnerSlot(doc.id, doc.getString("venueId").orEmpty(), doc.getString("courtId").orEmpty(), doc.getString("date").orEmpty(), doc.getTimestamp("startAt"), doc.getTimestamp("endAt"), doc.getString("status").orEmpty(), doc.getString("blockReason"), doc.getString("bookingId"), doc.getString("holdBy"))
        }

    suspend fun updateCourt(venueId: String, courtId: String, fields: CourtFields) {
        firestore.collection(VENUES).document(venueId).collection(COURTS).document(courtId)
            .update(fields.toMap()).await()
    }

    private fun toVenue(doc: com.google.firebase.firestore.DocumentSnapshot) = OwnerVenue(
        id = doc.id,
        ownerId = doc.getString("ownerId").orEmpty(),
        name = doc.getString("name").orEmpty(),
        address = doc.getString("address").orEmpty(),
        districtId = doc.getString("districtId").orEmpty(),
        location = doc.getGeoPoint("location"),
        openTime = doc.getString("openTime").orEmpty(),
        closeTime = doc.getString("closeTime").orEmpty(),
        phone = doc.getString("phone").orEmpty(),
        amenityIds = doc.get("amenityIds") as? List<String> ?: emptyList(),
        photoPaths = doc.get("photoPaths") as? List<String> ?: emptyList(),
        courtCount = (doc.getLong("courtCount") ?: 0L).toInt(),
        acceptsRecurring = doc.getBoolean("acceptsRecurring") ?: true,
        status = doc.getString("status").orEmpty()
    )

    private fun toCourt(doc: com.google.firebase.firestore.DocumentSnapshot) = OwnerCourt(
        id = doc.id,
        name = doc.getString("name").orEmpty(),
        surfaceType = doc.getString("surfaceType").orEmpty(),
        isActive = doc.getBoolean("isActive") ?: true,
        sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt()
    )

    private fun VenueEditableFields.toMap() = mapOf(
        "name" to name, "address" to address, "districtId" to districtId,
        "location" to location, "openTime" to openTime, "closeTime" to closeTime,
        "phone" to phone, "amenityIds" to amenityIds, "photoPaths" to photoPaths,
        "courtCount" to courtCount, "acceptsRecurring" to acceptsRecurring, "status" to status
    )

    private fun CourtFields.toMap() = mapOf(
        "name" to name, "surfaceType" to surfaceType,
        "isActive" to isActive, "sortOrder" to sortOrder
    )

    companion object {
        const val VENUES = "venues"
        const val COURTS = "courts"
        const val PRICE_RULES = "priceRules"
        const val SLOTS = "slots"
    }
}

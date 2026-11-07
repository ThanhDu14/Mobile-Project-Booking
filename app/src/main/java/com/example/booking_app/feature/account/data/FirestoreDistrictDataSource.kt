package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.feature.account.domain.model.District
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject

class FirestoreDistrictDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : DistrictDataSource {

    override suspend fun listActive(): List<District> =
        firestore.collection(COLLECTION)
            .whereEqualTo("isActive", true)
            .orderBy("sortOrder")
            .get()
            .awaitWithTimeout()
            .documents
            .mapNotNull { it.toDistrict() }

    override suspend fun get(id: String): District? =
        firestore.collection(COLLECTION).document(id).get().awaitWithTimeout().toDistrict()

    private fun DocumentSnapshot.toDistrict(): District? {
        if (!exists()) return null
        return District(
            id = id,
            name = getString("name") ?: id,
            sortOrder = getLong("sortOrder")?.toInt() ?: Int.MAX_VALUE,
            isActive = getBoolean("isActive") ?: false,
        )
    }

    private companion object {
        const val COLLECTION = "districts"
    }
}

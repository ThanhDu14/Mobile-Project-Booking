package com.example.booking_app.core.catalog

import com.example.booking_app.core.network.awaitWithTimeout
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject

/** Nguồn đọc danh mục; tách khỏi repository để test cache không cần Firestore. */
interface CatalogDataSource {
    suspend fun fetchDistricts(): List<District>
    suspend fun fetchAmenities(): List<Amenity>
}

class FirestoreCatalogDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : CatalogDataSource {

    override suspend fun fetchDistricts(): List<District> =
        firestore.collection("districts").get().awaitWithTimeout().documents.map { doc ->
            District(
                id = doc.id,
                name = doc.getString("name").orEmpty(),
                sortOrder = doc.getLong("sortOrder")?.toInt() ?: Int.MAX_VALUE,
                isActive = doc.getBoolean("isActive") ?: true,
            )
        }

    override suspend fun fetchAmenities(): List<Amenity> =
        firestore.collection("amenities").get().awaitWithTimeout().documents.map { doc ->
            Amenity(
                id = doc.id,
                name = doc.getString("name").orEmpty(),
                iconKey = doc.getString("iconKey").orEmpty(),
                sortOrder = doc.getLong("sortOrder")?.toInt() ?: Int.MAX_VALUE,
                isActive = doc.getBoolean("isActive") ?: true,
            )
        }
}

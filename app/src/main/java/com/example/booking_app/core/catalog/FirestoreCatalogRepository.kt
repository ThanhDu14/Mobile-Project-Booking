package com.example.booking_app.core.catalog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Cache danh mục trong bộ nhớ: lần đầu đọc Firestore, các lần sau trả bản đã có. Lỗi thì lần sau đọc lại. */
@Singleton
class FirestoreCatalogRepository @Inject constructor(
    private val source: CatalogDataSource,
) : CatalogRepository {

    private val mutex = Mutex()
    private val districts = MutableStateFlow<List<District>?>(null)
    private val amenities = MutableStateFlow<List<Amenity>?>(null)

    override val districtsFlow: StateFlow<List<District>?> = districts.asStateFlow()
    override val amenitiesFlow: StateFlow<List<Amenity>?> = amenities.asStateFlow()

    override suspend fun districts(): List<District> = mutex.withLock {
        districts.value ?: source.fetchDistricts().sortedBy { it.sortOrder }.also { districts.value = it }
    }

    override suspend fun amenities(): List<Amenity> = mutex.withLock {
        amenities.value ?: source.fetchAmenities().sortedBy { it.sortOrder }.also { amenities.value = it }
    }

    override suspend fun isActiveDistrict(id: String): Boolean = districts().any { it.id == id && it.isActive }

    override suspend fun isActiveAmenity(id: String): Boolean = amenities().any { it.id == id && it.isActive }
}

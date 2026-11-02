package com.example.booking_app.feature.account.data

import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.model.District
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Giữ danh mục trong bộ nhớ suốt phiên (danh mục hầu như không đổi); chỉ đọc lẻ khu vực đã ngừng. */
@Singleton
class DistrictRepositoryImpl @Inject constructor(
    private val dataSource: DistrictDataSource,
) : DistrictRepository {

    private val mutex = Mutex()
    private var active: List<District>? = null

    override suspend fun activeDistricts(): List<District> = mutex.withLock {
        active ?: accountCall { dataSource.listActive() }.sortedBy { it.sortOrder }.also { active = it }
    }

    override suspend fun districtsByIds(ids: Collection<String>): Map<String, District> {
        val known = activeDistricts().associateBy { it.id }
        val missing = ids.filterNot { it in known }.distinct()
        val extra = missing.mapNotNull { id -> accountCall { dataSource.get(id) } }
        return (known.values + extra).filter { it.id in ids }.associateBy { it.id }
    }
}

package com.example.booking_app.core.catalog

import com.example.booking_app.core.network.AppError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreCatalogRepositoryTest {

    private class FakeSource : CatalogDataSource {
        var districtCalls = 0
        var amenityCalls = 0
        var failNext: AppError? = null
        override suspend fun fetchDistricts(): List<District> {
            districtCalls++
            failNext?.let { failNext = null; throw it }
            return listOf(
                District("q3", "Quận 3", 2, true),
                District("q1", "Quận 1", 1, true),
                District("old", "Cũ", 3, false),
            )
        }
        override suspend fun fetchAmenities(): List<Amenity> {
            amenityCalls++
            failNext?.let { failNext = null; throw it }
            return listOf(
                Amenity("drinks", "Nước uống", "drinks", 2, true),
                Amenity("parking", "Gửi xe", "parking", 1, true),
                Amenity("sauna", "Xông hơi", "sauna", 3, false),
            )
        }
    }

    @Test
    fun `doc mot lan moi phien va sap theo sortOrder`() = runTest {
        val source = FakeSource()
        val repo = FirestoreCatalogRepository(source)
        assertEquals(listOf("q1", "q3", "old"), repo.districts().map { it.id })
        repo.districts()
        assertEquals(1, source.districtCalls)
        assertEquals(listOf("parking", "drinks", "sauna"), repo.amenities().map { it.id })
        repo.amenities()
        assertEquals(1, source.amenityCalls)
        assertEquals(listOf("q1", "q3", "old"), repo.districtsFlow.value?.map { it.id })
    }

    @Test
    fun `isActive tra false khi khong co hoac da tat`() = runTest {
        val repo = FirestoreCatalogRepository(FakeSource())
        assertTrue(repo.isActiveDistrict("q1"))
        assertFalse(repo.isActiveDistrict("old"))
        assertFalse(repo.isActiveDistrict("khong-co"))
        assertTrue(repo.isActiveAmenity("parking"))
        assertFalse(repo.isActiveAmenity("sauna"))
        assertFalse(repo.isActiveAmenity("khong-co"))
    }

    @Test
    fun `loi mang nem AppError va goi lai duoc`() = runTest {
        val source = FakeSource().apply { failNext = AppError.Network }
        val repo = FirestoreCatalogRepository(source)
        try {
            repo.districts()
            fail("Phải ném lỗi")
        } catch (e: AppError) {
            assertEquals(AppError.Network, e)
        }
        assertEquals(2, repo.districts().count { it.isActive })
        assertEquals(2, source.districtCalls)
    }
}

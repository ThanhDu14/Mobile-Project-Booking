package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.District
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DistrictRepositoryImplTest {

    private val dataSource = FakeDistrictDataSource()
    private val repository = DistrictRepositoryImpl(dataSource)

    @Test
    fun `danh muc sap theo sortOrder`() = runTest {
        assertEquals(listOf("q1", "q3", "q10"), repository.activeDistricts().map { it.id })
    }

    @Test
    fun `khu vuc da ngung van lay duoc ten de hien thi`() = runTest {
        val byId = repository.districtsByIds(listOf("q10", "q-cu", "khong-ton-tai"))
        assertEquals(setOf("q10", "q-cu"), byId.keys)
        assertEquals("Quận cũ", byId.getValue("q-cu").name)
        assertEquals(false, byId.getValue("q-cu").isActive)
    }

    @Test
    fun `da tai danh muc thi khong doc lai tung khu vuc dang hoat dong`() = runTest {
        repository.activeDistricts()
        repository.districtsByIds(listOf("q1", "q3"))
        assertEquals(emptyList<String>(), dataSource.getCalls)
    }

    @Test
    fun `loi mang thanh AccountError Network`() = runTest {
        dataSource.error = AppError.Timeout
        try {
            repository.activeDistricts()
            fail("phải ném lỗi")
        } catch (e: AccountError) {
            assertEquals(AccountError.Network, e)
        }
    }
}

class FakeDistrictDataSource : DistrictDataSource {
    private val all = listOf(
        District("q10", "Quận 10", 10),
        District("q1", "Quận 1", 1),
        District("q3", "Quận 3", 3),
        District("q-cu", "Quận cũ", 99, isActive = false),
    )
    var error: Throwable? = null
    val getCalls = mutableListOf<String>()

    override suspend fun listActive(): List<District> {
        error?.let { throw it }
        return all.filter { it.isActive }
    }

    override suspend fun get(id: String): District? {
        error?.let { throw it }
        getCalls += id
        return all.firstOrNull { it.id == id }
    }
}

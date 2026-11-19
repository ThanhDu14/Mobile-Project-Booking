package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.AdminDistrict
import com.example.booking_app.feature.admin.domain.model.AdminSystemStats
import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
import com.example.booking_app.feature.admin.domain.model.BroadcastNotificationRequest
import com.example.booking_app.feature.admin.domain.repository.AdminToolsRepository
import com.example.booking_app.feature.admin.ui.AdminToolsViewModel
import com.example.booking_app.feature.admin.ui.DistrictsUiState
import com.example.booking_app.feature.admin.ui.SystemStatsUiState
import com.example.booking_app.feature.admin.ui.SystemVouchersUiState
import com.example.booking_app.feature.admin.ui.ToolsActionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminToolsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAdminToolsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAdminToolsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadStats_whenStatsHasData_emitsContent() = runTest {
        fakeRepository.statsToReturn = AdminSystemStats(
            totalUsers = 1500,
            totalVenues = 45,
            totalBookings = 3200,
            totalRevenue = 500_000_000,
            hasData = true,
        )

        val vm = AdminToolsViewModel(fakeRepository)
        advanceUntilIdle()

        val state = vm.statsState.value
        assertTrue(state is SystemStatsUiState.Content)
        assertEquals(1500L, (state as SystemStatsUiState.Content).stats.totalUsers)
        assertEquals(45L, state.stats.totalVenues)
    }

    @Test
    fun loadStats_whenNoData_emitsEmpty() = runTest {
        fakeRepository.statsToReturn = AdminSystemStats(hasData = false)

        val vm = AdminToolsViewModel(fakeRepository)
        advanceUntilIdle()

        assertTrue(vm.statsState.value is SystemStatsUiState.Empty)
    }

    @Test
    fun loadVouchers_whenListNotEmpty_emitsContent() = runTest {
        val voucher = AdminSystemVoucher(
            code = "CHAOBAN",
            type = "FIXED",
            value = 30000,
            validFrom = "2026-11-01T00:00:00Z",
            validTo = "2026-11-30T23:59:59Z",
            usageLimit = 50,
            perUserLimit = 1,
        )
        fakeRepository.vouchersToReturn = listOf(voucher)

        val vm = AdminToolsViewModel(fakeRepository)
        advanceUntilIdle()

        val state = vm.vouchersState.value
        assertTrue(state is SystemVouchersUiState.Content)
        assertEquals(1, (state as SystemVouchersUiState.Content).vouchers.size)
        assertEquals("CHAOBAN", state.vouchers[0].code)
    }

    @Test
    fun createVoucher_validationFailure_emitsError() = runTest {
        val invalidVoucher = AdminSystemVoucher(
            code = "A", // too short
            type = "FIXED",
            value = 10000,
            validFrom = "2026-11-01T00:00:00Z",
            validTo = "2026-11-30T23:59:59Z",
            usageLimit = 10,
            perUserLimit = 1,
        )

        val vm = AdminToolsViewModel(fakeRepository)
        vm.createVoucher(invalidVoucher)
        advanceUntilIdle()

        val state = vm.voucherActionState.value
        assertTrue(state is ToolsActionUiState.Error)
        assertEquals("Mã voucher phải từ 3 đến 20 ký tự chữ hoa hoặc số", (state as ToolsActionUiState.Error).message)
    }

    @Test
    fun createVoucher_success_callsRepoAndEmitsSuccess() = runTest {
        val validVoucher = AdminSystemVoucher(
            code = "KM2026",
            type = "PERCENTAGE",
            value = 15,
            validFrom = "2026-11-01T00:00:00Z",
            validTo = "2026-11-30T23:59:59Z",
            usageLimit = 100,
            perUserLimit = 1,
        )

        val vm = AdminToolsViewModel(fakeRepository)
        vm.createVoucher(validVoucher)
        advanceUntilIdle()

        assertEquals("KM2026", fakeRepository.lastCreatedVoucher?.code)
        assertTrue(vm.voucherActionState.value is ToolsActionUiState.Success)
    }

    @Test
    fun toggleVoucher_callsRepoAndEmitsSuccess() = runTest {
        val vm = AdminToolsViewModel(fakeRepository)
        vm.toggleVoucher("KM2026", false)
        advanceUntilIdle()

        assertEquals("KM2026", fakeRepository.lastToggledCode)
        assertEquals(false, fakeRepository.lastToggledActive)
        assertTrue(vm.voucherActionState.value is ToolsActionUiState.Success)
    }

    @Test
    fun broadcastNotification_validationFailure_emitsError() = runTest {
        val vm = AdminToolsViewModel(fakeRepository)
        vm.broadcastNotification("   ", "Nội dung")
        advanceUntilIdle()

        val state = vm.broadcastActionState.value
        assertTrue(state is ToolsActionUiState.Error)
        assertEquals("Tiêu đề thông báo không được để trống", (state as ToolsActionUiState.Error).message)
    }

    @Test
    fun broadcastNotification_success_callsRepoAndEmitsSuccessWithCount() = runTest {
        fakeRepository.broadcastCountToReturn = 120

        val vm = AdminToolsViewModel(fakeRepository)
        vm.broadcastNotification("Bảo trì hệ thống", "Hệ thống sẽ bảo trì từ 0h đến 2h sáng mai.")
        advanceUntilIdle()

        assertEquals("Bảo trì hệ thống", fakeRepository.lastBroadcastRequest?.title)
        val state = vm.broadcastActionState.value
        assertTrue(state is ToolsActionUiState.Success)
        assertTrue((state as ToolsActionUiState.Success).message.contains("120"))
    }

    @Test
    fun toggleFailure_refreshesAuthoritativeVoucherState() = runTest {
        val vm = AdminToolsViewModel(fakeRepository)
        advanceUntilIdle()
        val previous = fakeRepository.voucherLoads
        fakeRepository.failToggle = true
        vm.toggleVoucher("LOCAL10", false)
        advanceUntilIdle()
        assertEquals(previous + 1, fakeRepository.voucherLoads)
        assertTrue(vm.voucherActionState.value is ToolsActionUiState.Error)
    }
}

private class FakeAdminToolsRepository : AdminToolsRepository {
    var statsToReturn: AdminSystemStats = AdminSystemStats(hasData = false)
    var vouchersToReturn: List<AdminSystemVoucher> = emptyList()
    var districtsToReturn: List<AdminDistrict> = emptyList()

    var lastCreatedVoucher: AdminSystemVoucher? = null
    var lastToggledCode: String? = null
    var lastToggledActive: Boolean? = null

    var lastSavedDistrict: AdminDistrict? = null
    var lastBroadcastRequest: BroadcastNotificationRequest? = null
    var broadcastCountToReturn: Int = 0
    var failToggle = false
    var voucherLoads = 0

    override suspend fun getSystemStats(): Result<AdminSystemStats> {
        return Result.success(statsToReturn)
    }

    override suspend fun getSystemVouchers(): Result<List<AdminSystemVoucher>> {
        voucherLoads++
        return Result.success(vouchersToReturn)
    }

    override suspend fun createSystemVoucher(voucher: AdminSystemVoucher): Result<Unit> {
        lastCreatedVoucher = voucher
        return Result.success(Unit)
    }

    override suspend fun toggleVoucherActive(code: String, isActive: Boolean): Result<Unit> {
        lastToggledCode = code
        lastToggledActive = isActive
        if (failToggle) return Result.failure(IllegalStateException("Unknown commit result"))
        return Result.success(Unit)
    }

    override suspend fun getDistricts(): Result<List<AdminDistrict>> {
        return Result.success(districtsToReturn)
    }

    override suspend fun saveDistrict(district: AdminDistrict): Result<Unit> {
        lastSavedDistrict = district
        return Result.success(Unit)
    }

    override suspend fun broadcastSystemNotification(request: BroadcastNotificationRequest): Result<Int> {
        lastBroadcastRequest = request
        return Result.success(broadcastCountToReturn)
    }
}

package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.AdminReport
import com.example.booking_app.feature.admin.domain.model.ModerateReportDecision
import com.example.booking_app.feature.admin.domain.model.ReportAction
import com.example.booking_app.feature.admin.domain.model.ReportStatus
import com.example.booking_app.feature.admin.domain.model.ReportTargetType
import com.example.booking_app.feature.admin.domain.repository.AdminReportRepository
import com.example.booking_app.feature.admin.ui.AdminReportQueueState
import com.example.booking_app.feature.admin.ui.AdminReportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAdminReportRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAdminReportRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadReports_whenEmpty_exposesEmptyState() = runTest {
        val vm = AdminReportsViewModel(fakeRepository)
        advanceUntilIdle()

        assertTrue(vm.queueState.value is AdminReportQueueState.Empty)
    }

    @Test
    fun loadReports_whenHasReports_exposesContentState() = runTest {
        val report = createSampleReport("rep-1", ReportTargetType.VENUE, "venues/v1")
        fakeRepository.setOpenReports(listOf(report))

        val vm = AdminReportsViewModel(fakeRepository)
        advanceUntilIdle()

        val state = vm.queueState.value
        assertTrue(state is AdminReportQueueState.Content)
        assertEquals(1, (state as AdminReportQueueState.Content).reports.size)
        assertEquals("rep-1", state.reports[0].id)
    }

    @Test
    fun loadReports_whenError_exposesErrorState() = runTest {
        fakeRepository.shouldThrowOnObserve = true
        val vm = AdminReportsViewModel(fakeRepository)
        advanceUntilIdle()

        val state = vm.queueState.value
        assertTrue(state is AdminReportQueueState.Error)
        assertEquals("Lỗi kết nối", (state as AdminReportQueueState.Error).message)
    }

    @Test
    fun selectReport_updatesDetailAndLoadsTargetSummary() = runTest {
        fakeRepository.targetSummaryData = mapOf("name" to "Sân Vi Phạm")
        val vm = AdminReportsViewModel(fakeRepository)
        val report = createSampleReport("rep-venue", ReportTargetType.VENUE, "venues/v-bad")

        vm.selectReport(report)
        advanceUntilIdle()

        assertEquals(report, vm.detailState.value.report)
        assertEquals("Sân Vi Phạm", vm.detailState.value.targetSummary["name"])
    }

    @Test
    fun submitDismiss_success_setsSuccessMessage() = runTest {
        val vm = AdminReportsViewModel(fakeRepository)
        val report = createSampleReport("rep-dismiss", ReportTargetType.VENUE, "venues/v1")
        vm.selectReport(report)

        var completed = false
        vm.submitModeration(ReportAction.NONE) { completed = it }
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals("Đã bỏ qua báo cáo", vm.detailState.value.successMessage)
        assertEquals("rep-dismiss", fakeRepository.lastDecision?.reportId)
        assertEquals(ReportAction.NONE, fakeRepository.lastDecision?.action)
    }

    @Test
    fun submitAction_lockVenue_success_setsSuccessMessage() = runTest {
        val vm = AdminReportsViewModel(fakeRepository)
        val report = createSampleReport("rep-venue", ReportTargetType.VENUE, "venues/v1")
        vm.selectReport(report)

        var completed = false
        vm.submitModeration(ReportAction.LOCK_VENUE) { completed = it }
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals("Đã xử lý vi phạm thành công", vm.detailState.value.successMessage)
        assertEquals(ReportAction.LOCK_VENUE, fakeRepository.lastDecision?.action)
    }

    @Test
    fun submitAction_mismatchedAction_setsErrorMessageAndDoesNotCallRepo() = runTest {
        val vm = AdminReportsViewModel(fakeRepository)
        val report = createSampleReport("rep-user", ReportTargetType.USER, "users/u1")
        vm.selectReport(report)

        var completed = true
        vm.submitModeration(ReportAction.LOCK_VENUE) { completed = it }
        advanceUntilIdle()

        assertEquals(false, completed)
        assertEquals("Báo cáo người dùng chỉ có thể áp dụng Khóa tài khoản", vm.detailState.value.errorMessage)
        assertNull(fakeRepository.lastDecision)
    }

    @Test
    fun submitAction_onRepositoryFailure_setsErrorMessage() = runTest {
        fakeRepository.shouldFailModerate = true
        val vm = AdminReportsViewModel(fakeRepository)
        val report = createSampleReport("rep-fail", ReportTargetType.VENUE, "venues/v1")
        vm.selectReport(report)

        var completed = true
        vm.submitModeration(ReportAction.LOCK_VENUE) { completed = it }
        advanceUntilIdle()

        assertEquals(false, completed)
        assertNotNull(vm.detailState.value.errorMessage)
    }

    private fun createSampleReport(
        id: String,
        targetType: ReportTargetType,
        targetPath: String,
    ): AdminReport {
        return AdminReport(
            id = id,
            reporterId = "reporter-1",
            targetType = targetType,
            targetPath = targetPath,
            reason = "Lý do báo cáo vi phạm",
            detail = "Chi tiết cụ thể vi phạm",
            status = ReportStatus.OPEN,
            action = ReportAction.NONE,
            createdAt = 1000L,
        )
    }
}

private class FakeAdminReportRepository : AdminReportRepository {
    private val openFlow = MutableSharedFlow<List<AdminReport>>(replay = 1)
    var shouldThrowOnObserve = false
    var shouldFailModerate = false
    var lastDecision: ModerateReportDecision? = null
    var targetSummaryData: Map<String, Any?> = emptyMap()

    init {
        openFlow.tryEmit(emptyList())
    }

    fun setOpenReports(list: List<AdminReport>) {
        openFlow.tryEmit(list)
    }

    override fun observeOpenReports(): Flow<List<AdminReport>> {
        if (shouldThrowOnObserve) {
            return flow { throw RuntimeException("Lỗi kết nối") }
        }
        return openFlow
    }

    override suspend fun getReport(reportId: String): Result<AdminReport> {
        return Result.success(
            AdminReport(
                id = reportId,
                reporterId = "rep-u",
                targetType = ReportTargetType.VENUE,
                targetPath = "venues/v1",
                reason = "Lý do",
                detail = "Chi tiết",
                status = ReportStatus.OPEN,
                createdAt = 1000L,
            )
        )
    }

    override suspend fun moderateReport(decision: ModerateReportDecision): Result<Unit> {
        lastDecision = decision
        if (shouldFailModerate) {
            return Result.failure(RuntimeException("Xung đột xử lý báo cáo"))
        }
        return Result.success(Unit)
    }

    override suspend fun getTargetSummary(targetPath: String): Result<Map<String, Any?>> {
        return Result.success(targetSummaryData)
    }
}

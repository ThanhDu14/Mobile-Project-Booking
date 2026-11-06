package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.AdminOwnerApplication
import com.example.booking_app.feature.admin.domain.model.ApplicationReviewDecision
import com.example.booking_app.feature.admin.domain.model.ReviewAction
import com.example.booking_app.feature.admin.domain.repository.AdminApplicationRepository
import com.example.booking_app.feature.admin.ui.AdminApplicationsViewModel
import com.example.booking_app.feature.admin.ui.AdminQueueState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminApplicationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAdminApplicationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAdminApplicationRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadQueue_whenEmpty_exposesEmptyState() = runTest {
        val vm = AdminApplicationsViewModel(fakeRepository)
        advanceUntilIdle()

        assertTrue(vm.queueState.value is AdminQueueState.Empty)
    }

    @Test
    fun loadQueue_whenHasItems_exposesContentState() = runTest {
        val app = createSampleApplication("app-1", "Sân Cầu Lông A")
        fakeRepository.setPendingApplications(listOf(app))

        val vm = AdminApplicationsViewModel(fakeRepository)
        advanceUntilIdle()

        val state = vm.queueState.value
        assertTrue(state is AdminQueueState.Content)
        assertEquals(1, (state as AdminQueueState.Content).applications.size)
        assertEquals("Sân Cầu Lông A", state.applications[0].venueName)
    }

    @Test
    fun loadQueue_whenError_exposesErrorState() = runTest {
        fakeRepository.shouldThrowOnObserve = true
        val vm = AdminApplicationsViewModel(fakeRepository)
        advanceUntilIdle()

        val state = vm.queueState.value
        assertTrue(state is AdminQueueState.Error)
        assertEquals("Lỗi kết nối", (state as AdminQueueState.Error).message)
    }

    @Test
    fun selectApplication_updatesDetailState() = runTest {
        val vm = AdminApplicationsViewModel(fakeRepository)
        val app = createSampleApplication("app-42", "Sân Số 42")

        vm.selectApplication(app)
        advanceUntilIdle()

        assertEquals(app, vm.detailState.value.application)
    }

    @Test
    fun submitApprove_success_setsSuccessMessage() = runTest {
        val vm = AdminApplicationsViewModel(fakeRepository)
        val app = createSampleApplication("app-approve", "Sân Duyệt")
        vm.selectApplication(app)

        var completed = false
        vm.submitDecision(ReviewAction.APPROVE) { completed = it }
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals("Duyệt hồ sơ thành công", vm.detailState.value.successMessage)
        assertEquals("app-approve", fakeRepository.lastDecision?.applicationId)
        assertEquals(ReviewAction.APPROVE, fakeRepository.lastDecision?.action)
    }

    @Test
    fun submitReject_withoutReason_setsValidationError() = runTest {
        val vm = AdminApplicationsViewModel(fakeRepository)
        val app = createSampleApplication("app-reject", "Sân Từ Chối")
        vm.selectApplication(app)
        vm.updateRejectReasonInput("   ")

        vm.submitDecision(ReviewAction.REJECT)
        advanceUntilIdle()

        assertEquals("Vui lòng nhập lý do từ chối", vm.detailState.value.errorMessage)
        assertEquals(null, fakeRepository.lastDecision)
    }

    @Test
    fun submitReject_withReason_callsRepositoryAndSetsSuccess() = runTest {
        val vm = AdminApplicationsViewModel(fakeRepository)
        val app = createSampleApplication("app-reject", "Sân Từ Chối")
        vm.selectApplication(app)
        vm.updateRejectReasonInput("Giấy tờ không hợp lệ")

        var completed = false
        vm.submitDecision(ReviewAction.REJECT) { completed = it }
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals("Đã từ chối hồ sơ", vm.detailState.value.successMessage)
        assertEquals("app-reject", fakeRepository.lastDecision?.applicationId)
        assertEquals(ReviewAction.REJECT, fakeRepository.lastDecision?.action)
        assertEquals("Giấy tờ không hợp lệ", fakeRepository.lastDecision?.rejectReason)
    }

    @Test
    fun submitDecision_onFailure_preservesRejectReasonInput() = runTest {
        fakeRepository.shouldFailReview = true
        val vm = AdminApplicationsViewModel(fakeRepository)
        val app = createSampleApplication("app-fail", "Sân Lỗi")
        vm.selectApplication(app)
        vm.updateRejectReasonInput("Lý do quan trọng")

        vm.submitDecision(ReviewAction.REJECT)
        advanceUntilIdle()

        assertNotNull(vm.detailState.value.errorMessage)
        assertEquals("Lý do quan trọng", vm.detailState.value.rejectReasonInput)
    }

    @Test
    fun loadDocumentSignedUrl_savesUrlToState() = runTest {
        val vm = AdminApplicationsViewModel(fakeRepository)
        val app = createSampleApplication("app-docs", "Sân Tài Liệu")
        vm.selectApplication(app)

        vm.loadDocumentSignedUrl("docs/license.png")
        advanceUntilIdle()

        assertEquals("https://signed.url/license.png", vm.detailState.value.documentUrls["docs/license.png"])
    }

    private fun createSampleApplication(id: String, venueName: String): AdminOwnerApplication {
        return AdminOwnerApplication(
            id = id,
            applicantId = "user-$id",
            applicantName = "Nguyễn Văn Đại Diện",
            applicantPhone = "0901234567",
            venueName = venueName,
            venueAddress = "123 Đường Cầu Lông, Q1",
            districtId = "q1",
            courtCount = 4,
            openTime = "0600",
            closeTime = "2200",
            venuePhone = "0281234567",
            status = "PENDING",
            submittedAt = 1000L,
        )
    }
}

private class FakeAdminApplicationRepository : AdminApplicationRepository {
    private val pendingFlow = MutableSharedFlow<List<AdminOwnerApplication>>(replay = 1)
    var shouldThrowOnObserve = false
    var shouldFailReview = false
    var lastDecision: ApplicationReviewDecision? = null

    init {
        pendingFlow.tryEmit(emptyList())
    }

    fun setPendingApplications(list: List<AdminOwnerApplication>) {
        pendingFlow.tryEmit(list)
    }

    override fun observePendingApplications(): Flow<List<AdminOwnerApplication>> {
        if (shouldThrowOnObserve) {
            return flow { throw RuntimeException("Lỗi kết nối") }
        }
        return pendingFlow
    }

    override suspend fun getApplication(applicationId: String): Result<AdminOwnerApplication> {
        return Result.success(
            AdminOwnerApplication(
                id = applicationId,
                applicantId = "user-1",
                applicantName = "Nguyễn Văn A",
                applicantPhone = "0901234567",
                venueName = "Sân Chi Tiết",
                venueAddress = "456 Đường ABC",
                districtId = "q1",
                courtCount = 3,
                openTime = "0500",
                closeTime = "2300",
                venuePhone = "0287654321",
                status = "PENDING",
                submittedAt = 2000L,
            )
        )
    }

    override suspend fun reviewApplication(decision: ApplicationReviewDecision): Result<Unit> {
        lastDecision = decision
        if (shouldFailReview) {
            return Result.failure(RuntimeException("Hồ sơ đã được xử lý bởi quản trị viên khác"))
        }
        return Result.success(Unit)
    }

    override suspend fun getSignedDocumentUrl(applicationId: String, path: String): Result<String> {
        return Result.success("https://signed.url/${path.substringAfterLast('/')}")
    }
}

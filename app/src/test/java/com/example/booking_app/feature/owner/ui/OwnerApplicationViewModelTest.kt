package com.example.booking_app.feature.owner.ui

import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.model.District
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationSnapshot
import com.example.booking_app.feature.owner.domain.OwnerApplicationState
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatus
import com.example.booking_app.feature.owner.domain.OwnerOnboardingRepository
import com.example.booking_app.feature.owner.domain.OwnerVenueDraft
import com.example.booking_app.feature.owner.domain.UploadedOwnerFile
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerApplicationViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun newLifecycleRestoresLocalDraftEvenWhenOpenedFromApplyAction() = runTest(mainDispatcherRule.dispatcher) {
        val draft = validDraft().copy(representativeName = "Draft chưa gửi")
        val repository = FakeRepository().apply { localDraft = "local-lifecycle" to draft }
        val viewModel = viewModel(repository)

        viewModel.initialize(forceNew = true)
        advanceUntilIdle()

        assertEquals(draft, viewModel.uiState.value.draft)
        assertEquals("local-lifecycle", viewModel.uiState.value.applicationId)
    }

    @Test
    fun revokedLifecycleGetsNewIdButKeepsApplicantDraft() = runTest(mainDispatcherRule.dispatcher) {
        val draft = validDraft().copy(representativeName = "Giữ lại để đăng ký lại")
        val oldApplicationId = "old-approved-application"
        val repository = FakeRepository().apply {
            currentStatus = status(OwnerApplicationState.NONE, application = snapshot(oldApplicationId, OwnerApplicationState.APPROVED, draft))
            localDraft = oldApplicationId to draft
        }
        val viewModel = viewModel(repository)

        viewModel.initialize()
        advanceUntilIdle()

        assertEquals(draft, viewModel.uiState.value.draft)
        assertNotEquals(oldApplicationId, viewModel.uiState.value.applicationId)
    }

    @Test
    fun rejectedLifecycleUsesAuthoritativeApplicationAndReason() = runTest(mainDispatcherRule.dispatcher) {
        val rejected = snapshot("rejected-app", OwnerApplicationState.REJECTED, validDraft(), "Thiếu giấy tờ")
        val repository = FakeRepository().apply {
            currentStatus = status(OwnerApplicationState.REJECTED, rejected)
            localDraft = "stale-local" to validDraft().copy(representativeName = "Bản nháp cũ")
        }
        val viewModel = viewModel(repository)

        viewModel.initialize(resubmit = true)
        advanceUntilIdle()

        assertEquals("rejected-app", viewModel.uiState.value.applicationId)
        assertEquals(rejected.draft, viewModel.uiState.value.draft)
        assertEquals("Thiếu giấy tờ", viewModel.uiState.value.status?.application?.rejectReason)
    }

    @Test
    fun allOwnerStatusesAndTrustedRolesAreMappedFromRefresh() = runTest(mainDispatcherRule.dispatcher) {
        val cases = listOf(
            status(OwnerApplicationState.NONE),
            status(OwnerApplicationState.PENDING, snapshot("pending-app", OwnerApplicationState.PENDING, validDraft())),
            status(OwnerApplicationState.APPROVED, snapshot("approved-app", OwnerApplicationState.APPROVED, validDraft())),
            status(OwnerApplicationState.REJECTED, snapshot("rejected-app", OwnerApplicationState.REJECTED, validDraft(), "Sửa ảnh giấy tờ")),
        )

        cases.forEach { expected ->
            val repository = FakeRepository().apply { currentStatus = expected }
            val viewModel = viewModel(repository)
            viewModel.initialize()
            advanceUntilIdle()
            assertEquals(expected.ownerStatus, viewModel.uiState.value.status?.ownerStatus)
            assertEquals(expected.role, viewModel.uiState.value.status?.role)
            assertTrue(viewModel.uiState.value.statusFresh)
            if (expected.ownerStatus == OwnerApplicationState.REJECTED) {
                assertEquals("Sửa ảnh giấy tờ", viewModel.uiState.value.status?.application?.rejectReason)
            }
        }
    }

    @Test
    fun statusBecomesStaleAfterRefreshFailure() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRepository().apply { currentStatus = status(OwnerApplicationState.APPROVED) }
        val viewModel = viewModel(repository)
        viewModel.initialize()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.statusFresh)

        repository.refreshError = IllegalStateException("offline")
        viewModel.refreshStatus()
        advanceUntilIdle()

        assertEquals(OwnerApplicationState.APPROVED, viewModel.uiState.value.status?.ownerStatus)
        assertFalse(viewModel.uiState.value.statusFresh)
    }

    @Test
    fun submitRefreshesStatusAndDoesNotSendWhenPending() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRepository().apply { currentStatus = status(OwnerApplicationState.NONE) }
        val viewModel = viewModel(repository)
        viewModel.initialize()
        advanceUntilIdle()
        fillDraft(viewModel, validDraft())
        repository.currentStatus = status(OwnerApplicationState.PENDING)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.submitCalls)
        assertEquals(OwnerApplicationState.PENDING, viewModel.uiState.value.status?.ownerStatus)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun rejectedResubmitUsesSameApplicationId() = runTest(mainDispatcherRule.dispatcher) {
        val rejected = snapshot("rejected-app", OwnerApplicationState.REJECTED, validDraft(), "Thiếu giấy tờ")
        val repository = FakeRepository().apply { currentStatus = status(OwnerApplicationState.REJECTED, rejected) }
        val viewModel = viewModel(repository)
        viewModel.initialize(resubmit = true)
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("rejected-app", repository.submittedApplicationId)
        assertEquals(1, repository.submitCalls)
        assertEquals(OwnerApplicationState.PENDING, viewModel.uiState.value.status?.ownerStatus)
    }

    @Test
    fun staleRejectedFormKeepsDraftAndDoesNotSubmitAfterAnotherDeviceMovesToPending() = runTest(mainDispatcherRule.dispatcher) {
        val rejected = snapshot("rejected-app", OwnerApplicationState.REJECTED, validDraft(), "Thiếu giấy tờ")
        val repository = FakeRepository().apply { currentStatus = status(OwnerApplicationState.REJECTED, rejected) }
        val viewModel = viewModel(repository)
        viewModel.initialize(resubmit = true)
        advanceUntilIdle()
        val retainedDraft = viewModel.uiState.value.draft.copy(representativeName = "Bản sửa giữ lại")
        viewModel.update("representativeName", retainedDraft.representativeName)
        repository.currentStatus = status(OwnerApplicationState.PENDING, snapshot("rejected-app", OwnerApplicationState.PENDING, validDraft()))

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.submitCalls)
        assertEquals(retainedDraft, viewModel.uiState.value.draft)
        assertEquals(OwnerApplicationState.PENDING, viewModel.uiState.value.status?.ownerStatus)
    }

    @Test
    fun failedFileCanBeRetriedIndependently() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRepository().apply {
            currentStatus = status(OwnerApplicationState.NONE)
            uploadErrorOnce = IllegalStateException("offline")
        }
        val viewModel = viewModel(repository)
        viewModel.initialize()
        advanceUntilIdle()

        viewModel.addAttachment("license.jpg", "image/jpeg", byteArrayOf(1, 2, 3), document = true).join()
        val failed = viewModel.uiState.value.uploads.single()
        assertTrue(failed.failed)
        assertTrue(viewModel.uiState.value.draft.documentPaths.isEmpty())

        viewModel.retryAttachment(failed.id)
        advanceUntilIdle()

        assertEquals(2, repository.uploadCalls)
        assertTrue(viewModel.uiState.value.uploads.isEmpty())
        assertEquals(listOf("uid/${viewModel.uiState.value.applicationId}/license.jpg"), viewModel.uiState.value.draft.documentPaths)
    }

    @Test
    fun signedDocumentUrlIsRequestedOnDemandAndNeverAddedToDraft() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        val before = viewModel.uiState.value.draft

        val result = viewModel.signedDocumentUrl("application-1", "uid/application-1/license.jpg")

        assertEquals("https://signed.test/uid/application-1/license.jpg", result.getOrThrow())
        assertEquals("application-1" to "uid/application-1/license.jpg", repository.signedDocumentRequest)
        assertEquals(before, viewModel.uiState.value.draft)
    }

    @Test
    fun duplicateSubmitWhileRequestIsRunningOnlySubmitsOnce() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRepository().apply { currentStatus = status(OwnerApplicationState.NONE) }
        val viewModel = viewModel(repository)
        viewModel.initialize()
        advanceUntilIdle()
        fillDraft(viewModel, validDraft())
        val submit = CompletableDeferred<Result<OwnerApplicationStatus>>()
        repository.submitGate = submit

        viewModel.submit()
        runCurrent()
        viewModel.submit()
        runCurrent()
        assertEquals(1, repository.submitCalls)

        submit.complete(Result.success(status(OwnerApplicationState.PENDING)))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.busy)
        assertEquals(OwnerApplicationState.PENDING, viewModel.uiState.value.status?.ownerStatus)
    }

    @Test
    fun lostSubmitResponseRefreshesCommittedPendingStateWithoutDiscardingDraft() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRepository().apply { currentStatus = status(OwnerApplicationState.NONE) }
        val viewModel = viewModel(repository)
        viewModel.initialize()
        advanceUntilIdle()
        fillDraft(viewModel, validDraft())
        val retainedDraft = viewModel.uiState.value.draft
        repository.submitErrorOnce = IllegalStateException("timeout")
        repository.statusAfterSubmitError = status(OwnerApplicationState.PENDING)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.submitCalls)
        assertEquals(OwnerApplicationState.PENDING, viewModel.uiState.value.status?.ownerStatus)
        assertTrue(viewModel.uiState.value.statusFresh)
        assertEquals(retainedDraft, viewModel.uiState.value.draft)
        assertFalse(viewModel.uiState.value.busy)
    }

    private fun viewModel(repository: FakeRepository) = OwnerApplicationViewModel(repository, FakeDistrictRepository())

    private suspend fun fillDraft(viewModel: OwnerApplicationViewModel, draft: OwnerApplicationDraft) {
        viewModel.update("venueName", draft.venue.name)
        viewModel.update("address", draft.venue.address)
        viewModel.update("districtId", draft.venue.districtId)
        viewModel.updateLocation(draft.venue.location!!.latitude, draft.venue.location.longitude)
        viewModel.update("venuePhone", draft.venue.phone)
        viewModel.update("courtCount", draft.venue.courtCount.toString())
        viewModel.update("openTime", draft.venue.openTime)
        viewModel.update("closeTime", draft.venue.closeTime)
        viewModel.update("representativeName", draft.representativeName)
        viewModel.update("representativePhone", draft.representativePhone)
        viewModel.addAttachment("photo.jpg", "image/jpeg", byteArrayOf(1), document = false).join()
        viewModel.addAttachment("license.jpg", "image/jpeg", byteArrayOf(1), document = true).join()
    }

    private class FakeDistrictRepository : DistrictRepository {
        override suspend fun activeDistricts() = listOf(District("d10", "Quận 10", 10))
        override suspend fun districtsByIds(ids: Collection<String>) = emptyMap<String, District>()
    }

    private inner class FakeRepository : OwnerOnboardingRepository {
        var currentStatus = status(OwnerApplicationState.NONE)
        var refreshError: Throwable? = null
        var localDraft: Pair<String, OwnerApplicationDraft>? = null
        var submitCalls = 0
        var uploadCalls = 0
        var uploadErrorOnce: Throwable? = null
        var submitErrorOnce: Throwable? = null
        var statusAfterSubmitError: OwnerApplicationStatus? = null
        var submittedApplicationId: String? = null
        var signedDocumentRequest: Pair<String, String>? = null
        var submitGate: CompletableDeferred<Result<OwnerApplicationStatus>>? = null

        override suspend fun refreshStatus(): Result<OwnerApplicationStatus> = refreshError?.let { Result.failure(it) } ?: Result.success(currentStatus)
        override suspend fun upload(fileName: String, contentType: String, bytes: ByteArray, applicationId: String): Result<UploadedOwnerFile> {
            uploadCalls++
            uploadErrorOnce?.let { error -> uploadErrorOnce = null; return Result.failure(error) }
            return Result.success(UploadedOwnerFile("uid/$applicationId/$fileName", fileName, contentType))
        }
        override suspend fun submit(applicationId: String, draft: OwnerApplicationDraft): Result<OwnerApplicationStatus> {
            submitCalls++
            submittedApplicationId = applicationId
            submitErrorOnce?.let { error ->
                submitErrorOnce = null
                statusAfterSubmitError?.let { currentStatus = it }
                return Result.failure(error)
            }
            return submitGate?.await() ?: Result.success(status(OwnerApplicationState.PENDING))
        }
        override suspend fun signedDocumentUrl(applicationId: String, path: String): Result<String> {
            signedDocumentRequest = applicationId to path
            return Result.success("https://signed.test/$path")
        }
        override suspend fun downloadDocument(applicationId: String, path: String) = Result.success(byteArrayOf(1, 2, 3))
        override suspend fun deleteStaged(path: String) = Result.success(Unit)
        override suspend fun saveLocalDraft(applicationId: String, draft: OwnerApplicationDraft) = Result.success(Unit)
        override suspend fun loadLocalDraft() = Result.success(localDraft)
        override suspend fun clearLocalDraft() = Result.success(Unit)
    }

    private fun status(state: OwnerApplicationState, application: OwnerApplicationSnapshot? = null) =
        OwnerApplicationStatus(state, if (state == OwnerApplicationState.APPROVED) "OWNER" else "PLAYER", application)

    private fun snapshot(
        id: String,
        state: OwnerApplicationState,
        draft: OwnerApplicationDraft,
        reason: String? = null,
    ) = OwnerApplicationSnapshot(id, draft, state, reason, "2026-11-05T08:00:00Z")

    private fun validDraft() = OwnerApplicationDraft(
        venue = OwnerVenueDraft(
            "Sân Nam Phát", "88 Sư Vạn Hạnh", "d10", GeoPoint(10.77, 106.66),
            "028 3862 1145", 5, "05:30", "22:30", listOf("uid/app/photo.jpg"),
        ),
        representativeName = "Nguyễn Thị Lan",
        representativePhone = "0903 412 778",
        documentPaths = listOf("uid/app/license.jpg"),
    )
}

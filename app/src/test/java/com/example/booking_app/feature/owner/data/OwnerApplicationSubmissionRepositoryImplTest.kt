package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMetadata
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusRepository
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusSnapshot
import com.example.booking_app.feature.owner.domain.OwnerApplicationReviewStatus
import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmitCommand
import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmitResult
import com.example.booking_app.feature.owner.domain.OwnerRole
import com.example.booking_app.feature.owner.domain.OwnerStatus
import com.example.booking_app.feature.owner.domain.VenueDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OwnerApplicationSubmissionRepositoryImplTest {
    @Test
    fun `timeout then authoritative matching pending record is reported submitted`() = runBlocking {
        val api = mockk<OwnerApplicationSubmitApi>()
        val status = mockk<OwnerApplicationStatusRepository>()
        coEvery { api.submit(applicationId, draft) } throws AppError.Timeout
        coEvery { status.getAuthoritativeStatus() } returns snapshot(OwnerStatus.PENDING, applicationId)

        val result = OwnerApplicationSubmissionRepositoryImpl(api, status).submit(command)

        assertEquals(OwnerApplicationSubmitResult.Submitted(applicationId), result)
    }

    @Test
    fun `acknowledged rejected resubmit is successful only after matching pending read back`() = runBlocking {
        val api = mockk<OwnerApplicationSubmitApi>()
        val status = mockk<OwnerApplicationStatusRepository>()
        coEvery { api.submit(applicationId, draft) } returns applicationId
        coEvery { status.getAuthoritativeStatus() } returns snapshot(OwnerStatus.PENDING, applicationId)

        val result = OwnerApplicationSubmissionRepositoryImpl(api, status).submit(command)

        assertEquals(OwnerApplicationSubmitResult.Submitted(applicationId), result)
        coVerify(exactly = 1) { status.getAuthoritativeStatus() }
    }

    @Test
    fun `timeout with none retries same application identity and confirms read back`() = runBlocking {
        val api = mockk<OwnerApplicationSubmitApi>()
        val status = mockk<OwnerApplicationStatusRepository>()
        coEvery { api.submit(applicationId, draft) } throws AppError.Timeout andThen applicationId
        coEvery { status.getAuthoritativeStatus() } returnsMany listOf(
            snapshot(OwnerStatus.NONE, null),
            snapshot(OwnerStatus.PENDING, applicationId),
        )

        val result = OwnerApplicationSubmissionRepositoryImpl(api, status).submit(command)

        assertEquals(OwnerApplicationSubmitResult.Submitted(applicationId), result)
    }

    @Test
    fun `timeout with different pending application returns conflict and keeps caller draft`() = runBlocking {
        val api = mockk<OwnerApplicationSubmitApi>()
        val status = mockk<OwnerApplicationStatusRepository>()
        coEvery { api.submit(applicationId, draft) } throws AppError.Timeout
        coEvery { status.getAuthoritativeStatus() } returns snapshot(OwnerStatus.PENDING, "other-application")

        val result = OwnerApplicationSubmissionRepositoryImpl(api, status).submit(command)

        assertEquals(OwnerApplicationSubmitResult.Conflict, result)
        assertEquals("Keep this draft", command.draft.venue.name)
    }

    @Test
    fun `matching application already moved out of pending is not reported as a current successful submit`() = runBlocking {
        val api = mockk<OwnerApplicationSubmitApi>()
        val status = mockk<OwnerApplicationStatusRepository>()
        coEvery { api.submit(applicationId, draft) } returns applicationId
        coEvery { status.getAuthoritativeStatus() } returns snapshot(OwnerStatus.REJECTED, applicationId)

        val result = OwnerApplicationSubmissionRepositoryImpl(api, status).submit(command)

        assertEquals(OwnerApplicationSubmitResult.Conflict, result)
    }

    @Test
    fun `timeout with unavailable authoritative read is ambiguous and does not retry blindly`() = runBlocking {
        val api = mockk<OwnerApplicationSubmitApi>()
        val status = mockk<OwnerApplicationStatusRepository>()
        coEvery { api.submit(applicationId, draft) } throws AppError.Timeout
        coEvery { status.getAuthoritativeStatus() } throws IllegalStateException("offline")

        val result = OwnerApplicationSubmissionRepositoryImpl(api, status).submit(command)

        assertEquals(OwnerApplicationSubmitResult.Ambiguous, result)
        coVerify(exactly = 1) { api.submit(applicationId, draft) }
    }

    @Test
    fun `successful response without matching authoritative read back remains ambiguous`() = runBlocking {
        val api = mockk<OwnerApplicationSubmitApi>()
        val status = mockk<OwnerApplicationStatusRepository>()
        coEvery { api.submit(applicationId, draft) } returns applicationId
        coEvery { status.getAuthoritativeStatus() } returns snapshot(OwnerStatus.NONE, null)

        val result = OwnerApplicationSubmissionRepositoryImpl(api, status).submit(command)

        assertEquals(OwnerApplicationSubmitResult.Ambiguous, result)
    }

    @Test
    fun `typed conflict from server is preserved for refresh while draft remains caller owned`() = runBlocking {
        val api = mockk<OwnerApplicationSubmitApi>()
        val status = mockk<OwnerApplicationStatusRepository>()
        coEvery { api.submit(applicationId, draft) } throws AppError.Unknown(
            FunctionHttpException(409, """{"error":{"code":"CONFLICT_STALE"}}"""),
        )

        val result = OwnerApplicationSubmissionRepositoryImpl(api, status).submit(command)

        assertEquals(OwnerApplicationSubmitResult.Conflict, result)
        assertEquals("Keep this draft", command.draft.venue.name)
        coVerify(exactly = 0) { status.getAuthoritativeStatus() }
    }

    private fun snapshot(ownerStatus: OwnerStatus, id: String?) = OwnerApplicationStatusSnapshot(
        ownerStatus = ownerStatus,
        role = if (ownerStatus == OwnerStatus.APPROVED) OwnerRole.OWNER else OwnerRole.PLAYER,
        application = id?.let {
            OwnerApplicationStatusMetadata(
                id = it,
                status = when (ownerStatus) {
                    OwnerStatus.PENDING -> OwnerApplicationReviewStatus.PENDING
                    OwnerStatus.APPROVED -> OwnerApplicationReviewStatus.APPROVED
                    OwnerStatus.REJECTED -> OwnerApplicationReviewStatus.REJECTED
                    OwnerStatus.NONE -> error("NONE has no application")
                },
                submittedAt = Instant.parse("2026-10-26T00:00:00Z"),
                rejectReason = null,
            )
        },
    )

    private companion object {
        const val applicationId = "Abcdefghijklmnopqrst"
        val draft = OwnerApplicationDraft(
            venue = VenueDraft(
                name = "Keep this draft", address = "1 Street", districtId = "d1",
                latitude = 10.0, longitude = 106.0, phone = "0900000000", courtCount = 1,
                openTime = "0600", closeTime = "2200", photoPaths = listOf("u/a/photo.jpg"),
            ),
            representativeName = "Applicant", representativePhone = "0900000001",
            documentPaths = listOf("u/a/document.jpg"),
        )
        val command = OwnerApplicationSubmitCommand(applicationId, draft)
    }
}

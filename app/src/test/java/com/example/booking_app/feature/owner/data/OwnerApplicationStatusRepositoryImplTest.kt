package com.example.booking_app.feature.owner.data

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OwnerApplicationStatusRepositoryImplTest {
    @Test
    fun `rejected response maps editable applicant fields and keeps server identity`() = runBlocking {
        val api = mockk<OwnerApplicationStatusApi>()
        coEvery { api.fetch() } returns OwnerApplicationStatusResponse(
            ownerStatus = "REJECTED",
            role = "PLAYER",
            application = OwnerApplicationStatusResponseApplication(
                id = "rejected-application",
                status = "REJECTED",
                submittedAt = "2026-10-26T00:00:00Z",
                rejectReason = "Thiếu giấy tờ",
                venueDraft = OwnerApplicationStatusVenueDraft(
                    name = "Sân cũ",
                    address = "1 Đường A",
                    districtId = "district-1",
                    location = OwnerApplicationStatusLocation(10.78, 106.69),
                    phone = "0900000000",
                    courtCount = 2,
                    openTime = "0600",
                    closeTime = "2200",
                    photoPaths = listOf("uid/rejected-application/photo.jpg"),
                ),
                representativeName = "Người đại diện",
                representativePhone = "0900000001",
                documentPaths = listOf("uid/rejected-application/document.jpg"),
            ),
        )

        val result = OwnerApplicationStatusRepositoryImpl(api).getAuthoritativeStatus()
        val application = requireNotNull(result.application)
        val draft = requireNotNull(application.rejectedDraft)

        assertEquals("rejected-application", application.id)
        assertEquals("Thiếu giấy tờ", application.rejectReason)
        assertEquals("Sân cũ", draft.venue.name)
        assertEquals(10.78, draft.venue.latitude!!, 0.0)
        assertEquals("Người đại diện", draft.representativeName)
        assertEquals("uid/rejected-application/document.jpg", draft.documentPaths.single())
    }
}

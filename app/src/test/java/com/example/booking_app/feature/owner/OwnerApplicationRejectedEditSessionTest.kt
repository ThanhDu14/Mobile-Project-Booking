package com.example.booking_app.feature.owner

import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationRejectedEditSession
import com.example.booking_app.feature.owner.domain.OwnerApplicationReviewStatus
import com.example.booking_app.feature.owner.domain.RejectedEditResult
import com.example.booking_app.feature.owner.domain.VenueDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerApplicationRejectedEditSessionTest {
    @Test
    fun `authoritative rejected keeps session editable`() {
        val session = OwnerApplicationRejectedEditSession(draft(), "Thiếu giấy tờ")

        assertEquals(RejectedEditResult.Ready, session.applyAuthoritativeStatus(OwnerApplicationReviewStatus.REJECTED))
        assertEquals("Thiếu giấy tờ", session.rejectReason)
    }

    @Test
    fun `status changed while editing returns conflict and keeps draft`() {
        val session = OwnerApplicationRejectedEditSession(draft(), "Thiếu giấy tờ")
        val edited = draft().copy(representativeName = "Tên mới")
        session.updateDraft(edited)

        assertEquals(RejectedEditResult.Conflict, session.applyAuthoritativeStatus(OwnerApplicationReviewStatus.PENDING))
        assertEquals(edited, session.draft)
    }

    @Test
    fun `draft update never changes reject reason`() {
        val session = OwnerApplicationRejectedEditSession(draft(), "Lý do")
        session.updateDraft(draft().copy(representativePhone = "0900000002"))

        assertTrue(session.draft.representativePhone.endsWith("0002"))
        assertEquals("Lý do", session.rejectReason)
    }

    private fun draft() = OwnerApplicationDraft(
        venue = VenueDraft(
            name = "Sân thử",
            address = "1 Đường A",
            districtId = "q1",
            latitude = 10.78,
            longitude = 106.69,
            phone = "0900000000",
            courtCount = 2,
            openTime = "0600",
            closeTime = "2200",
            photoPaths = listOf("venue.jpg"),
        ),
        representativeName = "Người đại diện",
        representativePhone = "0900000001",
        documentPaths = listOf("document.jpg"),
    )
}

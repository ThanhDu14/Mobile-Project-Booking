package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.OwnerApplicationReviewStatus
import com.example.booking_app.feature.owner.domain.OwnerRole
import com.example.booking_app.feature.owner.domain.OwnerStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.util.Date

class OwnerApplicationMapperTest {
    @Test
    fun `account status va application status duoc anh xa theo enum rieng`() {
        val account = OwnerApplicationMapper.accountState(mapOf("role" to "PLAYER", "ownerStatus" to "NONE"))
        assertEquals(OwnerRole.PLAYER, account.role)
        assertEquals(OwnerStatus.NONE, account.ownerStatus)

        val application = OwnerApplicationMapper.application("application-id", applicationFields())
        assertNotNull(application)
        assertEquals(OwnerApplicationReviewStatus.PENDING, application?.status)
        assertNull(OwnerApplicationReviewStatus.fromWire("NONE"))
    }

    @Test
    fun `mapper doc cac field applicant va review co san khong tao default khi sai schema`() {
        val submittedAt = Instant.parse("2026-10-01T12:00:00Z")
        val fields = applicationFields().toMutableMap().apply {
            put("status", "REJECTED")
            put("rejectReason", "Bổ sung giấy tờ")
            put("reviewedBy", "admin-id")
            put("reviewedAt", Timestamp(Date.from(submittedAt)))
            put("submittedAt", Timestamp(Date.from(submittedAt)))
        }

        val application = OwnerApplicationMapper.application("application-id", fields)
        assertEquals("applicant-id", application?.uid)
        assertEquals("Bổ sung giấy tờ", application?.rejectReason)
        assertEquals("admin-id", application?.reviewedBy)
        assertEquals(submittedAt, application?.reviewedAt)
        assertEquals(submittedAt, application?.submittedAt)
        assertEquals(2, application?.venueDraft?.courtCount)
        assertNull(OwnerApplicationMapper.application("application-id", fields - "uid"))
    }

    private fun applicationFields() = mapOf<String, Any?>(
        "uid" to "applicant-id",
        "venueDraft" to mapOf(
            "name" to "Sân Cầu Lông",
            "address" to "1 Đường A",
            "districtId" to "district-id",
            "location" to GeoPoint(10.78, 106.69),
            "phone" to "0900000000",
            "courtCount" to 2,
            "openTime" to "0600",
            "closeTime" to "2200",
            "photoPaths" to listOf("venue/photo.jpg"),
        ),
        "representativeName" to "Nguyễn An",
        "representativePhone" to "0900000001",
        "documentPaths" to listOf("applicant-id/application-id/document.jpg"),
        "status" to "PENDING",
    )
}

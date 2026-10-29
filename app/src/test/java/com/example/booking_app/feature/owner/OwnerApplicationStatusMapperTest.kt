package com.example.booking_app.feature.owner

import com.example.booking_app.feature.owner.domain.OwnerAccountState
import com.example.booking_app.feature.owner.domain.OwnerApplication
import com.example.booking_app.feature.owner.domain.OwnerApplicationReviewStatus
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMapper
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusFormatter
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMapping
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMetadata
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusSnapshot
import com.example.booking_app.feature.owner.domain.OwnerRole
import com.example.booking_app.feature.owner.domain.OwnerStatus
import com.example.booking_app.feature.owner.domain.VenueDraft
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerApplicationStatusMapperTest {
    @Test
    fun `submitted timestamp is formatted in Vietnam local time`() {
        assertEquals(
            "26/10/2026 07:00",
            OwnerApplicationStatusFormatter.submittedAt(Instant.parse("2026-10-26T00:00:00Z")),
        )
        assertEquals(null, OwnerApplicationStatusFormatter.submittedAt(null))
    }

    @Test
    fun `authoritative snapshots map all four owner statuses and metadata`() {
        val at = Instant.parse("2026-10-26T00:00:00Z")
        val cases = listOf(
            OwnerApplicationStatusSnapshot(OwnerStatus.NONE, OwnerRole.PLAYER, null) to OwnerStatus.NONE,
            OwnerApplicationStatusSnapshot(
                OwnerStatus.PENDING, OwnerRole.PLAYER,
                OwnerApplicationStatusMetadata("pending", OwnerApplicationReviewStatus.PENDING, at, null),
            ) to OwnerStatus.PENDING,
            OwnerApplicationStatusSnapshot(
                OwnerStatus.APPROVED, OwnerRole.OWNER,
                OwnerApplicationStatusMetadata("approved", OwnerApplicationReviewStatus.APPROVED, at, null),
            ) to OwnerStatus.APPROVED,
            OwnerApplicationStatusSnapshot(
                OwnerStatus.REJECTED, OwnerRole.PLAYER,
                OwnerApplicationStatusMetadata("rejected", OwnerApplicationReviewStatus.REJECTED, at, "Thiếu giấy tờ"),
            ) to OwnerStatus.REJECTED,
        )

        cases.forEach { (snapshot, expected) ->
            val mapped = content(OwnerApplicationStatusMapper.map(snapshot))
            assertEquals(expected, mapped.ownerStatus)
            assertEquals(snapshot.application?.submittedAt, mapped.application?.submittedAt)
            assertEquals(snapshot.application?.rejectReason, mapped.application?.rejectReason)
        }
    }

    @Test
    fun `non none status without a uniquely identified application fails closed`() {
        val result = OwnerApplicationStatusMapper.map(
            OwnerApplicationStatusSnapshot(OwnerStatus.APPROVED, OwnerRole.OWNER, null),
        )

        assertTrue(result is OwnerApplicationStatusMapping.Ambiguous)
    }

    @Test
    fun `NONE is authoritative and does not use approved history`() {
        val result = OwnerApplicationStatusMapper.map(
            OwnerAccountState(OwnerRole.PLAYER, OwnerStatus.NONE),
            listOf(application(OwnerApplicationReviewStatus.APPROVED)),
        )

        assertEquals(OwnerStatus.NONE, (result as OwnerApplicationStatusMapping.Content).value.ownerStatus)
        assertEquals(null, result.value.application)
    }

    @Test
    fun `PENDING maps one matching application`() {
        val pending = application(OwnerApplicationReviewStatus.PENDING)
        val result = OwnerApplicationStatusMapper.map(
            OwnerAccountState(OwnerRole.PLAYER, OwnerStatus.PENDING),
            listOf(pending),
        )

        assertEquals(OwnerApplicationReviewStatus.PENDING, content(result).application?.status)
        assertEquals(pending.documentPaths, content(result).application?.documentPaths)
    }

    @Test
    fun `APPROVED maps matching application`() {
        val result = OwnerApplicationStatusMapper.map(
            OwnerAccountState(OwnerRole.OWNER, OwnerStatus.APPROVED),
            listOf(application(OwnerApplicationReviewStatus.APPROVED)),
        )

        assertEquals(OwnerStatus.APPROVED, content(result).ownerStatus)
    }

    @Test
    fun `REJECTED maps matching application and reason`() {
        val rejected = application(OwnerApplicationReviewStatus.REJECTED).copy(rejectReason = "Thiếu giấy tờ")
        val result = OwnerApplicationStatusMapper.map(
            OwnerAccountState(OwnerRole.PLAYER, OwnerStatus.REJECTED),
            listOf(rejected),
        )

        assertEquals("Thiếu giấy tờ", content(result).application?.rejectReason)
    }

    @Test
    fun `multiple matching applications are ambiguous instead of choosing by order`() {
        val result = OwnerApplicationStatusMapper.map(
            OwnerAccountState(OwnerRole.PLAYER, OwnerStatus.PENDING),
            listOf(application(OwnerApplicationReviewStatus.PENDING), application(OwnerApplicationReviewStatus.PENDING)),
        )

        assertTrue(result is OwnerApplicationStatusMapping.Ambiguous)
    }

    @Test
    fun `owner role mismatch is ambiguous and never grants access from application status`() {
        val result = OwnerApplicationStatusMapper.map(
            OwnerAccountState(OwnerRole.PLAYER, OwnerStatus.APPROVED),
            listOf(application(OwnerApplicationReviewStatus.APPROVED)),
        )

        assertTrue(result is OwnerApplicationStatusMapping.Ambiguous)
    }

    private fun content(result: OwnerApplicationStatusMapping) =
        (result as OwnerApplicationStatusMapping.Content).value

    private fun application(status: OwnerApplicationReviewStatus) = OwnerApplication(
        id = status.name.lowercase(),
        uid = "player-1",
        venueDraft = VenueDraft(
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
        status = status,
        rejectReason = null,
        reviewedBy = null,
        reviewedAt = null,
        submittedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}

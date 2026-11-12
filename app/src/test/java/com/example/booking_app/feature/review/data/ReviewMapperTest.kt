package com.example.booking_app.feature.review.data

import com.example.booking_app.feature.review.data.dto.ReviewBookingDto
import com.example.booking_app.feature.review.data.dto.ReviewDto
import com.example.booking_app.feature.review.data.dto.VenueRatingFieldsDto
import com.example.booking_app.feature.review.data.dto.toDomain
import com.example.booking_app.feature.review.data.dto.toSummary
import com.example.booking_app.feature.review.data.dto.toTarget
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.ReviewTargetType
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReviewMapperTest {

    private val completedAt = Instant.parse("2026-10-04T09:00:00Z")
    private val createdAt = Instant.parse("2026-10-04T10:00:00Z")

    private fun dto(
        userId: String? = "player1",
        authorDeleted: Boolean = false,
        status: String = "VISIBLE",
        editedAt: Instant? = null,
        replyText: String? = null,
        replyEditedAt: Instant? = null,
    ) = ReviewDto(
        id = "b1", venueId = "venueA", userId = userId,
        displayName = "Minh", avatarPath = "player1/a.jpg", authorDeleted = authorDeleted,
        court = 5, cleanliness = 4, service = 3, overall = 4.0,
        comment = "Sân sạch", photoPaths = listOf("player1/b1/x.jpg"),
        ownerReplyText = replyText, ownerRepliedAt = replyText?.let { createdAt }, ownerReplyEditedAt = replyEditedAt,
        status = status, completedAt = completedAt, editedAt = editedAt, createdAt = createdAt,
    )

    private fun map(d: ReviewDto) = d.toDomain(photoUrl = { "photo:$it" }, avatarUrl = { "avatar:$it" })

    @Test
    fun `targetType DROP_IN - map thanh DROP_IN, thieu targetType la BOOKING`() {
        assertEquals(ReviewTargetType.DROP_IN, map(dto().copy(targetType = "DROP_IN")).targetType)
        assertEquals(ReviewTargetType.BOOKING, map(dto()).targetType)
        assertEquals(ReviewTargetType.BOOKING, map(dto().copy(targetType = "BOOKING")).targetType)
    }

    @Test
    fun `review VISIBLE - map du truong, URL anh, han sua = completedAt + 7 ngay`() {
        val review = map(dto())
        assertEquals(Ratings(5, 4, 3), review.ratings)
        assertEquals("Minh", review.author.displayName)
        assertEquals("avatar:player1/a.jpg", review.author.avatarUrl)
        assertEquals(listOf("photo:player1/b1/x.jpg"), review.photoUrls)
        assertFalse(review.isHidden)
        assertFalse(review.isEdited)
        assertNull(review.ownerReply)
        assertEquals(completedAt.plus(Duration.ofDays(7)), review.canEditUntil)
    }

    @Test
    fun `tac gia da xoa tai khoan - isDeleted, bo ten va anh`() {
        listOf(dto(authorDeleted = true), dto(userId = null)).forEach {
            val author = map(it).author
            assertTrue(author.isDeleted)
            assertNull(author.displayName)
            assertNull(author.avatarUrl)
        }
    }

    @Test
    fun `editedAt, HIDDEN, phan hoi da sua`() {
        val review = map(dto(status = "HIDDEN", editedAt = createdAt, replyText = "Cảm ơn", replyEditedAt = createdAt))
        assertTrue(review.isHidden)
        assertTrue(review.isEdited)
        assertEquals("Cảm ơn", review.ownerReply?.text)
        assertTrue(review.ownerReply!!.isEdited)
    }

    @Test
    fun `tong diem - trung binh tung tieu chi tu ratingSums`() {
        val summary = VenueRatingFieldsDto(
            ratingAvg = 4.3,
            ratingCount = 3,
            ratingSums = mapOf("court" to 14L, "cleanliness" to 12L, "service" to 13L, "overall10" to 130L),
        ).toSummary()
        assertEquals(VenueRatingSummary(4.3, 3, 4.7, 4.0, 4.3), summary)
    }

    @Test
    fun `tong diem - chua co danh gia hoac du lieu cu thieu ratingSums`() {
        assertEquals(VenueRatingSummary(0.0, 0, 0.0, 0.0, 0.0), VenueRatingFieldsDto(null, null, null).toSummary())
        assertEquals(
            VenueRatingSummary(4.2, 5, 0.0, 0.0, 0.0),
            VenueRatingFieldsDto(4.2, 5, null).toSummary(),
        )
    }

    @Test
    fun `don - ten san, ten san con khong trung`() {
        val target = ReviewBookingDto(
            id = "b1", venueId = "venueA", venueName = "Sân Phú Thọ", venuePhotoUrl = null,
            courtNames = listOf("Sân 2", "Sân 2", "Sân 3"), startAt = null, endAt = null,
            status = "COMPLETED", completedAt = completedAt,
        ).toTarget()
        assertEquals("Sân Phú Thọ", target.venueName)
        assertEquals(listOf("Sân 2", "Sân 3"), target.courtNames)
    }
}

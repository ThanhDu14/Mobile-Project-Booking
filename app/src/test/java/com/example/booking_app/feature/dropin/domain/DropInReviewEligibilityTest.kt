package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.review
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** T057: nút đánh giá của lượt vãng lai (AC 5.1, 5.3). */
class DropInReviewEligibilityTest {

    private val endAt = Instant.parse("2026-11-10T14:00:00Z")

    private fun eligibility(
        status: String? = "CHECKED_IN",
        sessionEndAt: Instant? = endAt,
        hasReview: Boolean = false,
        now: Instant = endAt.plus(Duration.ofHours(1)),
    ) = ReviewEligibility.ofDropIn(status, sessionEndAt, if (hasReview) review("s1_u1") else null, now)

    @Test
    fun `CHECKED_IN, buoi da ket thuc, chua danh gia, trong 7 ngay - CAN_WRITE`() {
        assertEquals(ReviewEligibility.CAN_WRITE, eligibility())
    }

    @Test
    fun `dung luc buoi ket thuc van CAN_WRITE`() {
        assertEquals(ReviewEligibility.CAN_WRITE, eligibility(now = endAt))
    }

    @Test
    fun `dung moc endAt cong 7 ngay van CAN_WRITE`() {
        assertEquals(ReviewEligibility.CAN_WRITE, eligibility(now = endAt.plus(Duration.ofDays(7))))
    }

    @Test
    fun `qua moc 7 ngay 1 giay chua danh gia - EXPIRED`() {
        val now = endAt.plus(Duration.ofDays(7)).plusSeconds(1)
        assertEquals(ReviewEligibility.EXPIRED, eligibility(now = now))
    }

    @Test
    fun `da co danh gia - CAN_VIEW, ke ca khi da qua han`() {
        assertEquals(ReviewEligibility.CAN_VIEW, eligibility(hasReview = true))
        assertEquals(
            ReviewEligibility.CAN_VIEW,
            eligibility(hasReview = true, now = endAt.plus(Duration.ofDays(30))),
        )
    }

    @Test
    fun `REGISTERED, CANCELLED, WAITLISTED - NOT_ELIGIBLE`() {
        listOf("REGISTERED", "CANCELLED", "WAITLISTED", "NO_SHOW").forEach { status ->
            assertEquals(status, ReviewEligibility.NOT_ELIGIBLE, eligibility(status = status))
        }
    }

    @Test
    fun `buoi chua ket thuc - NOT_ELIGIBLE`() {
        assertEquals(ReviewEligibility.NOT_ELIGIBLE, eligibility(now = endAt.minusSeconds(1)))
    }

    @Test
    fun `thieu trang thai hoac gio ket thuc - NOT_ELIGIBLE`() {
        assertEquals(ReviewEligibility.NOT_ELIGIBLE, eligibility(status = null))
        assertEquals(ReviewEligibility.NOT_ELIGIBLE, eligibility(sessionEndAt = null))
    }

    @Test
    fun `da co danh gia thi CAN_VIEW du trang thai luot la gi`() {
        assertEquals(ReviewEligibility.CAN_VIEW, eligibility(status = "CANCELLED", hasReview = true))
    }
}

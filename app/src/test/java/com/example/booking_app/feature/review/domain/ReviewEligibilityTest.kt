package com.example.booking_app.feature.review.domain

import com.example.booking_app.feature.review.review
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReviewEligibilityTest {

    private val completedAt = Instant.parse("2026-10-04T09:00:00Z")

    private fun eligibility(
        status: String? = "COMPLETED",
        completedAt: Instant? = this.completedAt,
        hasReview: Boolean = false,
        now: Instant = this.completedAt.plus(Duration.ofDays(1)),
    ) = ReviewEligibility.of(status, completedAt, if (hasReview) review("b1") else null, now)

    @Test
    fun `COMPLETED, chua danh gia, trong 7 ngay - CAN_WRITE`() {
        assertEquals(ReviewEligibility.CAN_WRITE, eligibility())
    }

    @Test
    fun `dung moc 7 ngay van con viet duoc (khop server now le completedAt + 7 ngay)`() {
        assertEquals(ReviewEligibility.CAN_WRITE, eligibility(now = completedAt.plus(Duration.ofDays(7))))
    }

    @Test
    fun `da co danh gia - CAN_VIEW, ke ca khi da qua han`() {
        assertEquals(ReviewEligibility.CAN_VIEW, eligibility(hasReview = true))
        assertEquals(
            ReviewEligibility.CAN_VIEW,
            eligibility(hasReview = true, now = completedAt.plus(Duration.ofDays(30))),
        )
    }

    @Test
    fun `qua 7 ngay chua danh gia - EXPIRED`() {
        assertEquals(
            ReviewEligibility.EXPIRED,
            eligibility(now = completedAt.plus(Duration.ofDays(7)).plusSeconds(1)),
        )
    }

    @Test
    fun `trang thai khac COMPLETED - NOT_ELIGIBLE`() {
        listOf("HOLD", "PENDING", "CONFIRMED", "CANCELLED", "REJECTED", "EXPIRED", "NO_SHOW", null).forEach {
            assertEquals("status=$it", ReviewEligibility.NOT_ELIGIBLE, eligibility(status = it))
        }
    }

    @Test
    fun `COMPLETED nhung thieu completedAt (du lieu cu) - NOT_ELIGIBLE`() {
        assertEquals(ReviewEligibility.NOT_ELIGIBLE, eligibility(completedAt = null))
    }
}

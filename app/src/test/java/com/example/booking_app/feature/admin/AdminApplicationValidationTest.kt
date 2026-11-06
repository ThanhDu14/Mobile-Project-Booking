package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.ApplicationReviewDecision
import com.example.booking_app.feature.admin.domain.model.ReviewAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AdminApplicationValidationTest {

    @Test
    fun approveDecision_withValidApplicationId_isValid() {
        val decision = ApplicationReviewDecision(
            applicationId = "app-12345",
            action = ReviewAction.APPROVE,
        )
        assertNull(decision.validate())
    }

    @Test
    fun approveDecision_withBlankApplicationId_returnsError() {
        val decision = ApplicationReviewDecision(
            applicationId = "   ",
            action = ReviewAction.APPROVE,
        )
        assertEquals("Mã hồ sơ không hợp lệ", decision.validate())
    }

    @Test
    fun rejectDecision_withBlankReason_returnsError() {
        val decision = ApplicationReviewDecision(
            applicationId = "app-12345",
            action = ReviewAction.REJECT,
            rejectReason = "   ",
        )
        assertEquals("Vui lòng nhập lý do từ chối", decision.validate())
    }

    @Test
    fun rejectDecision_withNullReason_returnsError() {
        val decision = ApplicationReviewDecision(
            applicationId = "app-12345",
            action = ReviewAction.REJECT,
            rejectReason = null,
        )
        assertNotNull(decision.validate())
    }

    @Test
    fun rejectDecision_withValidReason_isValid() {
        val decision = ApplicationReviewDecision(
            applicationId = "app-12345",
            action = ReviewAction.REJECT,
            rejectReason = "  Giấy phép kinh doanh không khớp địa chỉ  ",
        )
        assertNull(decision.validate())
    }
}

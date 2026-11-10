package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.ModerateReportDecision
import com.example.booking_app.feature.admin.domain.model.ReportAction
import com.example.booking_app.feature.admin.domain.model.ReportTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AdminReportValidationTest {

    @Test
    fun dismissAction_withAnyTarget_isValid() {
        val decision = ModerateReportDecision(
            reportId = "rep-1",
            action = ReportAction.NONE,
            targetType = ReportTargetType.VENUE,
        )
        assertNull(decision.validate())
    }

    @Test
    fun blankReportId_returnsError() {
        val decision = ModerateReportDecision(
            reportId = "   ",
            action = ReportAction.LOCK_VENUE,
            targetType = ReportTargetType.VENUE,
        )
        assertEquals("Mã báo cáo không hợp lệ", decision.validate())
    }

    @Test
    fun venue_withLockVenue_isValid() {
        val decision = ModerateReportDecision(
            reportId = "rep-1",
            action = ReportAction.LOCK_VENUE,
            targetType = ReportTargetType.VENUE,
        )
        assertNull(decision.validate())
    }

    @Test
    fun venue_withHideContent_returnsMismatchError() {
        val decision = ModerateReportDecision(
            reportId = "rep-1",
            action = ReportAction.HIDE_CONTENT,
            targetType = ReportTargetType.VENUE,
        )
        assertEquals("Báo cáo cơ sở chỉ có thể áp dụng Khóa cơ sở", decision.validate())
    }

    @Test
    fun user_withLockUser_isValid() {
        val decision = ModerateReportDecision(
            reportId = "rep-2",
            action = ReportAction.LOCK_USER,
            targetType = ReportTargetType.USER,
        )
        assertNull(decision.validate())
    }

    @Test
    fun user_withLockVenue_returnsMismatchError() {
        val decision = ModerateReportDecision(
            reportId = "rep-2",
            action = ReportAction.LOCK_VENUE,
            targetType = ReportTargetType.USER,
        )
        assertEquals("Báo cáo người dùng chỉ có thể áp dụng Khóa tài khoản", decision.validate())
    }

    @Test
    fun post_withHideContent_isValid() {
        val decision = ModerateReportDecision(
            reportId = "rep-3",
            action = ReportAction.HIDE_CONTENT,
            targetType = ReportTargetType.POST,
        )
        assertNull(decision.validate())
    }

    @Test
    fun dropIn_withHideContent_returnsUnsupportedError() {
        val decision = ModerateReportDecision(
            reportId = "rep-4",
            action = ReportAction.HIDE_CONTENT,
            targetType = ReportTargetType.DROP_IN,
        )
        assertNotNull(decision.validate())
        assertEquals("Đối tượng DROP_IN chưa hỗ trợ thao tác ẩn theo quy định", decision.validate())
    }
}

package com.example.booking_app.feature.admin.domain.model

data class ModerateReportDecision(
    val reportId: String,
    val action: ReportAction,
    val targetType: ReportTargetType,
) {
    fun validate(): String? {
        if (reportId.isBlank()) return "Mã báo cáo không hợp lệ"
        if (action == ReportAction.NONE) return null

        return when (targetType) {
            ReportTargetType.VENUE -> {
                if (action != ReportAction.LOCK_VENUE) "Báo cáo cơ sở chỉ có thể áp dụng Khóa cơ sở" else null
            }
            ReportTargetType.USER -> {
                if (action != ReportAction.LOCK_USER) "Báo cáo người dùng chỉ có thể áp dụng Khóa tài khoản" else null
            }
            ReportTargetType.POST, ReportTargetType.MESSAGE, ReportTargetType.REVIEW -> {
                if (action != ReportAction.HIDE_CONTENT) "Báo cáo nội dung chỉ có thể áp dụng Ẩn nội dung" else null
            }
            ReportTargetType.DROP_IN -> "Đối tượng DROP_IN chưa hỗ trợ thao tác ẩn theo quy định"
            ReportTargetType.UNKNOWN -> "Loại đối tượng không hợp lệ"
        }
    }
}

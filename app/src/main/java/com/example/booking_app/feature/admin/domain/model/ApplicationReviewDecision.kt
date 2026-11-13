package com.example.booking_app.feature.admin.domain.model

enum class ReviewAction {
    APPROVE,
    REJECT
}

data class ApplicationReviewDecision(
    val applicationId: String,
    val action: ReviewAction,
    val rejectReason: String? = null,
    val expectedSubmittedAt: Long? = null,
) {
    fun validate(): String? {
        if (applicationId.isBlank()) {
            return "Mã hồ sơ không hợp lệ"
        }
        if (action == ReviewAction.REJECT) {
            val trimmed = rejectReason?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                return "Vui lòng nhập lý do từ chối"
            }
        }
        return null
    }
}

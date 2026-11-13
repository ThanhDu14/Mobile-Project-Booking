package com.example.booking_app.feature.admin.domain.model

data class BroadcastNotificationRequest(
    val title: String,
    val body: String,
    val requestId: String = java.util.UUID.randomUUID().toString(),
) {
    fun validate(): String? {
        if (title.trim().isBlank()) return "Tiêu đề thông báo không được để trống"
        if (body.trim().isBlank()) return "Nội dung thông báo không được để trống"
        return null
    }
}

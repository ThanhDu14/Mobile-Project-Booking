package com.example.booking_app.feature.admin.domain.model

enum class ReportTargetType {
    VENUE,
    USER,
    POST,
    MESSAGE,
    REVIEW,
    DROP_IN,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): ReportTargetType = runCatching {
            valueOf(value?.trim()?.uppercase().orEmpty())
        }.getOrDefault(UNKNOWN)
    }
}

enum class ReportStatus {
    OPEN,
    RESOLVED,
    DISMISSED;

    companion object {
        fun fromString(value: String?): ReportStatus = runCatching {
            valueOf(value?.trim()?.uppercase().orEmpty())
        }.getOrDefault(OPEN)
    }
}

enum class ReportAction {
    NONE,
    HIDE_CONTENT,
    LOCK_USER,
    LOCK_VENUE;

    companion object {
        fun fromString(value: String?): ReportAction = runCatching {
            valueOf(value?.trim()?.uppercase().orEmpty())
        }.getOrDefault(NONE)
    }
}

data class AdminReport(
    val id: String,
    val reporterId: String,
    val targetType: ReportTargetType,
    val targetPath: String,
    val reason: String,
    val detail: String,
    val status: ReportStatus,
    val action: ReportAction = ReportAction.NONE,
    val handledBy: String? = null,
    val handledAt: Long? = null,
    val createdAt: Long = 0L,
)

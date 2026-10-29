package com.example.booking_app.feature.owner.domain

/** Stable UI model for the authoritative account/application status screen. */
data class OwnerApplicationStatusPresentation(
    val ownerStatus: OwnerStatus,
    val application: OwnerApplicationStatusMetadata?,
)

data class OwnerApplicationStatusMetadata(
    val id: String,
    val status: OwnerApplicationReviewStatus,
    val submittedAt: java.time.Instant?,
    val rejectReason: String?,
    val rejectedDraft: OwnerApplicationDraft? = null,
    val documentPaths: List<String> = emptyList(),
)

data class OwnerApplicationStatusSnapshot(
    val ownerStatus: OwnerStatus?,
    val role: OwnerRole?,
    val application: OwnerApplicationStatusMetadata?,
)

object OwnerApplicationStatusFormatter {
    private val formatter = java.time.format.DateTimeFormatter
        .ofPattern("dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("vi-VN"))
        .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))

    fun submittedAt(value: java.time.Instant?): String? = value?.let(formatter::format)
}

sealed interface OwnerApplicationStatusMapping {
    data class Content(val value: OwnerApplicationStatusPresentation) : OwnerApplicationStatusMapping
    data object Empty : OwnerApplicationStatusMapping
    data object Ambiguous : OwnerApplicationStatusMapping
}

/**
 * Maps server reads without granting authority to an application document.
 * ownerStatus always comes from users/{uid}; an application is attached only when
 * exactly one record matches that status. Historical APPROVED records after revoke
 * therefore cannot grant owner access or silently become current.
 */
object OwnerApplicationStatusMapper {
    fun map(snapshot: OwnerApplicationStatusSnapshot): OwnerApplicationStatusMapping {
        val status = snapshot.ownerStatus ?: return OwnerApplicationStatusMapping.Ambiguous
        val roleMatchesStatus = when (status) {
            OwnerStatus.APPROVED -> snapshot.role == OwnerRole.OWNER
            OwnerStatus.NONE, OwnerStatus.PENDING, OwnerStatus.REJECTED -> snapshot.role == OwnerRole.PLAYER
        }
        if (!roleMatchesStatus) return OwnerApplicationStatusMapping.Ambiguous
        if (status == OwnerStatus.NONE) {
            return if (snapshot.application == null) {
                OwnerApplicationStatusMapping.Content(OwnerApplicationStatusPresentation(status, null))
            } else OwnerApplicationStatusMapping.Ambiguous
        }
        val application = snapshot.application ?: return OwnerApplicationStatusMapping.Ambiguous
        if (application.id.isBlank() || application.status.name != status.name) {
            return OwnerApplicationStatusMapping.Ambiguous
        }
        return OwnerApplicationStatusMapping.Content(OwnerApplicationStatusPresentation(status, application))
    }

    fun map(account: OwnerAccountState, applications: List<OwnerApplication>): OwnerApplicationStatusMapping {
        val status = account.ownerStatus ?: return OwnerApplicationStatusMapping.Ambiguous
        if (status == OwnerStatus.NONE) return map(OwnerApplicationStatusSnapshot(status, account.role, null))
        val matching = applications.filter { it.status.name == status.name }
        if (matching.size > 1) return OwnerApplicationStatusMapping.Ambiguous
        val application = matching.singleOrNull()
        return map(
            OwnerApplicationStatusSnapshot(
                ownerStatus = status,
                role = account.role,
                application = application?.let {
                    OwnerApplicationStatusMetadata(
                        it.id,
                        it.status,
                        it.submittedAt,
                        it.rejectReason,
                        documentPaths = it.documentPaths,
                    )
                },
            ),
        )
    }
}

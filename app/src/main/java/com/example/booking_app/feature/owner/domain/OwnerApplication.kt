package com.example.booking_app.feature.owner.domain

import java.time.Instant

/** Applicant-owned fields from the existing ownerApplications schema. */
data class OwnerApplicationDraft(
    val venue: VenueDraft,
    val representativeName: String,
    val representativePhone: String,
    val documentPaths: List<String>,
)

/** Fields already defined by the shared venues/ownerApplications database contract. */
data class VenueDraft(
    val name: String,
    val address: String,
    val districtId: String,
    val latitude: Double?,
    val longitude: Double?,
    val phone: String,
    val courtCount: Int?,
    /** Venue schema uses `openTime`/`closeTime` in HHmm format. */
    val openTime: String,
    val closeTime: String,
    val photoPaths: List<String>,
)

enum class OwnerStatus {
    NONE,
    PENDING,
    APPROVED,
    REJECTED;

    companion object {
        fun fromWire(value: String?): OwnerStatus? = entries.firstOrNull { it.name == value }
    }
}

enum class OwnerRole {
    PLAYER,
    OWNER,
    ADMIN;

    companion object {
        fun fromWire(value: String?): OwnerRole? = entries.firstOrNull { it.name == value }
    }
}

enum class OwnerApplicationReviewStatus {
    PENDING,
    APPROVED,
    REJECTED;

    companion object {
        fun fromWire(value: String?): OwnerApplicationReviewStatus? =
            entries.firstOrNull { it.name == value }
    }
}

/** Read-only view of `users/{uid}` fields relevant to owner onboarding. */
data class OwnerAccountState(
    val role: OwnerRole?,
    val ownerStatus: OwnerStatus?,
)

/** Read-only view of an existing `ownerApplications/{id}` document. */
data class OwnerApplication(
    val id: String,
    val uid: String,
    val venueDraft: VenueDraft,
    val representativeName: String,
    val representativePhone: String,
    val documentPaths: List<String>,
    val status: OwnerApplicationReviewStatus,
    val rejectReason: String?,
    val reviewedBy: String?,
    val reviewedAt: Instant?,
    val submittedAt: Instant?,
)

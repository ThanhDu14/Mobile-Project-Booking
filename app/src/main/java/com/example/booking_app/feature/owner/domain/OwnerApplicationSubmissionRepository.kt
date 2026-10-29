package com.example.booking_app.feature.owner.domain

data class OwnerApplicationSubmitCommand(
    val applicationId: String,
    val draft: OwnerApplicationDraft,
)

sealed interface OwnerApplicationSubmitResult {
    data class Submitted(val applicationId: String) : OwnerApplicationSubmitResult
    data object Conflict : OwnerApplicationSubmitResult
    data object Ambiguous : OwnerApplicationSubmitResult
}

interface OwnerApplicationSubmissionRepository {
    suspend fun submit(command: OwnerApplicationSubmitCommand): OwnerApplicationSubmitResult
}

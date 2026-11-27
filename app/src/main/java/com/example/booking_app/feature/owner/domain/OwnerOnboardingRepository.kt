package com.example.booking_app.feature.owner.domain

enum class OwnerApplicationState { NONE, PENDING, APPROVED, REJECTED }
enum class OwnerApplicationPurpose { OWNER_ONBOARDING, VENUE_ADDITION }

data class OwnerApplicationStatus(
    val ownerStatus: OwnerApplicationState,
    val role: String,
    val application: OwnerApplicationSnapshot?,
)

data class OwnerApplicationSnapshot(
    val applicationId: String,
    val draft: OwnerApplicationDraft,
    val status: OwnerApplicationState,
    val rejectReason: String?,
    val submittedAt: String?,
)

data class UploadedOwnerFile(val path: String, val displayName: String, val contentType: String)

interface OwnerOnboardingRepository {
    suspend fun applicationStatus(purpose: OwnerApplicationPurpose, applicationId: String?): Result<OwnerApplicationStatus> = refreshStatus()
    suspend fun applications(): Result<List<OwnerApplicationSnapshot>> = Result.success(emptyList())
    suspend fun uploadApplication(fileName: String, contentType: String, bytes: ByteArray, applicationId: String, purpose: OwnerApplicationPurpose): Result<UploadedOwnerFile> = upload(fileName, contentType, bytes, applicationId)
    suspend fun submitApplication(applicationId: String, draft: OwnerApplicationDraft, purpose: OwnerApplicationPurpose): Result<OwnerApplicationStatus> = submit(applicationId, draft)
    suspend fun saveDraft(applicationId: String, draft: OwnerApplicationDraft, purpose: OwnerApplicationPurpose): Result<Unit> = saveLocalDraft(applicationId, draft)
    suspend fun loadDraft(purpose: OwnerApplicationPurpose): Result<Pair<String, OwnerApplicationDraft>?> = loadLocalDraft()
    suspend fun refreshStatus(): Result<OwnerApplicationStatus>
    suspend fun upload(fileName: String, contentType: String, bytes: ByteArray, applicationId: String): Result<UploadedOwnerFile>
    suspend fun submit(applicationId: String, draft: OwnerApplicationDraft): Result<OwnerApplicationStatus>
    suspend fun signedDocumentUrl(applicationId: String, path: String): Result<String>
    suspend fun downloadDocument(applicationId: String, path: String): Result<ByteArray>
    suspend fun deleteStaged(path: String): Result<Unit>
    suspend fun saveLocalDraft(applicationId: String, draft: OwnerApplicationDraft): Result<Unit>
    suspend fun loadLocalDraft(): Result<Pair<String, OwnerApplicationDraft>?>
    suspend fun clearLocalDraft(): Result<Unit>
}

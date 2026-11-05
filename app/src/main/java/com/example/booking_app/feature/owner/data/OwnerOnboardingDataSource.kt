package com.example.booking_app.feature.owner.data

import android.content.Context
import android.content.SharedPreferences
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationSnapshot
import com.example.booking_app.feature.owner.domain.OwnerApplicationState
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatus
import com.example.booking_app.feature.owner.domain.OwnerOnboardingRepository
import com.example.booking_app.feature.owner.domain.OwnerVenueDraft
import com.example.booking_app.feature.owner.domain.UploadedOwnerFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OwnerApplicationRequest(
    val action: String,
    val applicationId: String? = null,
    val venueDraft: OwnerVenueRequest? = null,
    val representativeName: String? = null,
    val representativePhone: String? = null,
    val documentPaths: List<String>? = null,
)

@Serializable
data class OwnerVenueRequest(
    val name: String,
    val address: String,
    val districtId: String,
    val location: OwnerLocationRequest?,
    val phone: String,
    val courtCount: Int?,
    val openTime: String,
    val closeTime: String,
    val photoPaths: List<String>,
)

@Serializable
data class OwnerLocationRequest(val latitude: Double, val longitude: Double)

@Serializable
data class OwnerStatusResponse(
    val ownerStatus: String,
    val role: String,
    val application: OwnerApplicationResponse? = null,
    val alreadyCommitted: Boolean = false,
    val applicationId: String? = null,
)

@Serializable
data class OwnerApplicationResponse(
    val applicationId: String,
    val venueDraft: OwnerVenueRequest,
    val representativeName: String,
    val representativePhone: String,
    val documentPaths: List<String> = emptyList(),
    val status: String,
    val rejectReason: String? = null,
    val submittedAt: String? = null,
)

@Serializable
data class OwnerFileRequest(
    val action: String,
    val applicationId: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val path: String? = null,
)

@Serializable
data class OwnerFileResponse(
    val path: String? = null,
    val token: String? = null,
    val signedUrl: String? = null,
    val expiresInSeconds: Int? = null,
)

@Serializable
private data class LocalVenue(
    val name: String,
    val address: String,
    val districtId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val phone: String,
    val courtCount: Int? = null,
    val openTime: String,
    val closeTime: String,
    val photoPaths: List<String>,
)

@Serializable
private data class LocalDraft(
    val applicationId: String,
    val venue: LocalVenue,
    val representativeName: String,
    val representativePhone: String,
    val documentPaths: List<String>,
)

@Singleton
class OwnerOnboardingDataSource @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext context: Context,
) : OwnerOnboardingRepository {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val preferences: SharedPreferences = context.getSharedPreferences("owner-onboarding-drafts", Context.MODE_PRIVATE)
    private var lastUid: String? = firebaseAuth.currentUser?.uid

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val nextUid = auth.currentUser?.uid
            if (lastUid != null && lastUid != nextUid) clearAllDrafts()
            lastUid = nextUid
        }
    }

    override suspend fun refreshStatus(): Result<OwnerApplicationStatus> = runCatching {
        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(FUNCTION_APPLICATIONS, body = OwnerApplicationRequest(action = "STATUS"))
        }
        response.body<OwnerStatusResponse>().toDomain()
    }

    override suspend fun upload(
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        applicationId: String,
    ): Result<UploadedOwnerFile> = runCatching {
        require(contentType in ALLOWED_MIME && bytes.isNotEmpty() && bytes.size <= MAX_BYTES) {
            "Chỉ hỗ trợ JPEG, PNG hoặc WebP tối đa 2 MB mỗi tệp"
        }
        val signed = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_DOCS,
                body = OwnerFileRequest(
                    action = "SIGN_UPLOAD",
                    applicationId = applicationId,
                    fileName = fileName,
                    contentType = contentType,
                    sizeBytes = bytes.size.toLong(),
                ),
            ).body<OwnerFileResponse>()
        }
        val path = requireNotNull(signed.path)
        val token = requireNotNull(signed.token)
        supabase.storage.from(OWNER_DOCS_BUCKET).uploadToSignedUrl(path, token, bytes) {
            this.contentType = ContentType.parse(contentType)
        }
        UploadedOwnerFile(path, fileName, contentType)
    }

    override suspend fun submit(applicationId: String, draft: OwnerApplicationDraft): Result<OwnerApplicationStatus> = runCatching {
        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_APPLICATIONS,
                body = OwnerApplicationRequest(
                    action = "SUBMIT",
                    applicationId = applicationId,
                    venueDraft = draft.venue.toRequest(),
                    representativeName = draft.representativeName.trim(),
                    representativePhone = draft.representativePhone.trim(),
                    documentPaths = draft.documentPaths,
                ),
            )
        }
        response.body<OwnerStatusResponse>().let {
            val status = refreshStatus().getOrThrow()
            status.copy(application = status.application?.copy(applicationId = it.applicationId ?: applicationId))
        }
    }

    override suspend fun signedDocumentUrl(applicationId: String, path: String): Result<String> = runCatching {
        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_DOCS,
                body = OwnerFileRequest(action = "SIGN_DOWNLOAD", applicationId = applicationId, path = path),
            ).body<OwnerFileResponse>()
        }
        requireNotNull(response.signedUrl)
    }

    override suspend fun deleteStaged(path: String): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(FUNCTION_DOCS, body = OwnerFileRequest(action = "DELETE_STAGED", path = path))
        }
    }

    override suspend fun saveLocalDraft(applicationId: String, draft: OwnerApplicationDraft): Result<Unit> = runCatching {
        val uid = requireNotNull(firebaseAuth.currentUser?.uid)
        val local = LocalDraft(
            applicationId = applicationId,
            venue = LocalVenue(
                name = draft.venue.name,
                address = draft.venue.address,
                districtId = draft.venue.districtId,
                latitude = draft.venue.location?.latitude,
                longitude = draft.venue.location?.longitude,
                phone = draft.venue.phone,
                courtCount = draft.venue.courtCount,
                openTime = draft.venue.openTime,
                closeTime = draft.venue.closeTime,
                photoPaths = draft.venue.photoPaths,
            ),
            representativeName = draft.representativeName,
            representativePhone = draft.representativePhone,
            documentPaths = draft.documentPaths,
        )
        check(preferences.edit().putString(uid, json.encodeToString(local)).commit())
    }

    override suspend fun loadLocalDraft(): Result<Pair<String, OwnerApplicationDraft>?> = runCatching {
        val uid = requireNotNull(firebaseAuth.currentUser?.uid)
        val raw = preferences.getString(uid, null) ?: return@runCatching null
        val local = json.decodeFromString<LocalDraft>(raw)
        local.applicationId to OwnerApplicationDraft(
            venue = OwnerVenueDraft(
                name = local.venue.name,
                address = local.venue.address,
                districtId = local.venue.districtId,
                location = if (local.venue.latitude != null && local.venue.longitude != null) {
                    GeoPoint(local.venue.latitude, local.venue.longitude)
                } else null,
                phone = local.venue.phone,
                courtCount = local.venue.courtCount,
                openTime = local.venue.openTime,
                closeTime = local.venue.closeTime,
                photoPaths = local.venue.photoPaths,
            ),
            representativeName = local.representativeName,
            representativePhone = local.representativePhone,
            documentPaths = local.documentPaths,
        )
    }

    override suspend fun clearLocalDraft(): Result<Unit> = runCatching {
        firebaseAuth.currentUser?.uid?.let { preferences.edit().remove(it).commit() }
        Unit
    }

    private fun clearAllDrafts() { preferences.edit().clear().apply() }

    private fun OwnerStatusResponse.toDomain(): OwnerApplicationStatus {
        val parsedStatus = OwnerApplicationState.valueOf(ownerStatus)
        val snapshot = application?.let { app ->
            OwnerApplicationSnapshot(
                applicationId = app.applicationId,
                draft = OwnerApplicationDraft(
                    venue = OwnerVenueDraft(
                        name = app.venueDraft.name,
                        address = app.venueDraft.address,
                        districtId = app.venueDraft.districtId,
                        location = app.venueDraft.location?.let { GeoPoint(it.latitude, it.longitude) },
                        phone = app.venueDraft.phone,
                        courtCount = app.venueDraft.courtCount,
                        openTime = app.venueDraft.openTime,
                        closeTime = app.venueDraft.closeTime,
                        photoPaths = app.venueDraft.photoPaths,
                    ),
                    representativeName = app.representativeName,
                    representativePhone = app.representativePhone,
                    documentPaths = app.documentPaths,
                ),
                status = OwnerApplicationState.valueOf(app.status),
                rejectReason = app.rejectReason,
                submittedAt = app.submittedAt,
            )
        }
        return OwnerApplicationStatus(parsedStatus, role, snapshot)
    }

    private fun OwnerVenueDraft.toRequest() = OwnerVenueRequest(
        name = name,
        address = address,
        districtId = districtId,
        location = location?.let { OwnerLocationRequest(it.latitude, it.longitude) },
        phone = phone,
        courtCount = courtCount,
        openTime = openTime.replace(":", ""),
        closeTime = closeTime.replace(":", ""),
        photoPaths = photoPaths,
    )

    companion object {
        const val FUNCTION_APPLICATIONS = "owner-applications"
        const val FUNCTION_DOCS = "owner-docs"
        const val OWNER_DOCS_BUCKET = "owner-docs"
        const val MAX_BYTES = 2 * 1024 * 1024
        val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")
    }
}

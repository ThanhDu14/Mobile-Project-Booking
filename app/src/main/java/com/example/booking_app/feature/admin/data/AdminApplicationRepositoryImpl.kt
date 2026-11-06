package com.example.booking_app.feature.admin.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.admin.domain.model.AdminOwnerApplication
import com.example.booking_app.feature.admin.domain.model.ApplicationReviewDecision
import com.example.booking_app.feature.admin.domain.repository.AdminApplicationRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AdminReviewRequest(
    val applicationId: String,
    val decision: String,
    val rejectReason: String? = null,
)

@Serializable
data class AdminReviewResponse(
    val success: Boolean = false,
    val status: String? = null,
    val applicationId: String? = null,
    val alreadyCommitted: Boolean = false,
)

@Serializable
data class AdminDocDownloadRequest(
    val action: String,
    val applicationId: String,
    val path: String,
)

@Serializable
data class AdminDocDownloadResponse(
    val signedUrl: String? = null,
    val expiresInSeconds: Long? = null,
)

@Singleton
class AdminApplicationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : AdminApplicationRepository {

    override fun observePendingApplications(): Flow<List<AdminOwnerApplication>> = callbackFlow {
        val listener = firestore.collection("ownerApplications")
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toAdminOwnerApplication() }
                    ?.sortedByDescending { it.submittedAt }
                    .orEmpty()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getApplication(applicationId: String): Result<AdminOwnerApplication> = runCatching {
        val doc = firestore.collection("ownerApplications").document(applicationId).get().await()
        if (!doc.exists()) {
            throw NoSuchElementException("Không tìm thấy hồ sơ $applicationId")
        }
        doc.toAdminOwnerApplication() ?: throw IllegalStateException("Không thể đọc dữ liệu hồ sơ")
    }

    override suspend fun reviewApplication(decision: ApplicationReviewDecision): Result<Unit> = runCatching {
        val validationError = decision.validate()
        if (validationError != null) {
            throw IllegalArgumentException(validationError)
        }
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_REVIEW,
                body = AdminReviewRequest(
                    applicationId = decision.applicationId,
                    decision = decision.action.name,
                    rejectReason = decision.rejectReason?.trim(),
                ),
            ).body<AdminReviewResponse>()
        }
    }

    override suspend fun getSignedDocumentUrl(applicationId: String, path: String): Result<String> = runCatching {
        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_DOCS,
                body = AdminDocDownloadRequest(
                    action = "SIGN_DOWNLOAD",
                    applicationId = applicationId,
                    path = path,
                ),
            ).body<AdminDocDownloadResponse>()
        }
        requireNotNull(response.signedUrl) { "Không nhận được đường dẫn xem giấy tờ" }
    }

    private fun DocumentSnapshot.toAdminOwnerApplication(): AdminOwnerApplication? {
        val appId = id
        val applicantId = getString("uid") ?: getString("applicantId").orEmpty()
        val representativeName = getString("representativeName").orEmpty()
        val representativePhone = getString("representativePhone").orEmpty()
        val rawVenue = get("venueDraft") as? Map<*, *>
        val venueName = (rawVenue?.get("name") as? String).orEmpty()
        val venueAddress = (rawVenue?.get("address") as? String).orEmpty()
        val districtId = (rawVenue?.get("districtId") as? String).orEmpty()
        val courtCount = ((rawVenue?.get("courtCount") as? Number)?.toInt()) ?: 1
        val openTime = (rawVenue?.get("openTime") as? String).orEmpty()
        val closeTime = (rawVenue?.get("closeTime") as? String).orEmpty()
        val venuePhone = (rawVenue?.get("phone") as? String).orEmpty()
        val photoPaths = (rawVenue?.get("photoPaths") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val documentPaths = (get("documentPaths") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val status = getString("status").orEmpty().ifBlank { "PENDING" }
        val submittedAt = getTimestamp("submittedAt")?.toDate()?.time ?: 0L
        val rejectReason = getString("rejectReason")
        val reviewedBy = getString("reviewedBy")
        val reviewedAt = getTimestamp("reviewedAt")?.toDate()?.time

        return AdminOwnerApplication(
            id = appId,
            applicantId = applicantId,
            applicantName = representativeName,
            applicantPhone = representativePhone,
            venueName = venueName,
            venueAddress = venueAddress,
            districtId = districtId,
            courtCount = courtCount,
            openTime = openTime,
            closeTime = closeTime,
            venuePhone = venuePhone,
            photoPaths = photoPaths,
            documentPaths = documentPaths,
            status = status,
            submittedAt = submittedAt,
            rejectReason = rejectReason,
            reviewedBy = reviewedBy,
            reviewedAt = reviewedAt,
        )
    }

    companion object {
        private const val FUNCTION_REVIEW = "admin-review-application"
        private const val FUNCTION_DOCS = "owner-docs"
    }
}

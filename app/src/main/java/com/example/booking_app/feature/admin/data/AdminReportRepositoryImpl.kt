package com.example.booking_app.feature.admin.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.admin.domain.model.AdminReport
import com.example.booking_app.feature.admin.domain.model.ModerateReportDecision
import com.example.booking_app.feature.admin.domain.model.ReportAction
import com.example.booking_app.feature.admin.domain.model.ReportStatus
import com.example.booking_app.feature.admin.domain.model.ReportTargetType
import com.example.booking_app.feature.admin.domain.repository.AdminReportRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source
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
data class AdminModerateRequest(
    val reportId: String,
    val action: String,
)

@Serializable
data class AdminModerateResponse(
    val success: Boolean = false,
    val status: String? = null,
    val action: String? = null,
    val reportId: String? = null,
    val alreadyCommitted: Boolean = false,
)

@Singleton
class AdminReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : AdminReportRepository {

    override fun observeOpenReports(): Flow<List<AdminReport>> = observeReports(false, 30)

    override fun observeReports(closed: Boolean, limit: Long): Flow<List<AdminReport>> = callbackFlow {
        val listener = firestore.collection("reports")
            .whereIn("status", if (closed) listOf("RESOLVED", "DISMISSED") else listOf("OPEN"))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId())
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toAdminReport() }
                    ?.sortedByDescending { it.createdAt }
                    .orEmpty()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getReport(reportId: String): Result<AdminReport> = adminRunCatching {
        val doc = firestore.collection("reports").document(reportId).get(Source.SERVER).await()
        if (!doc.exists()) {
            throw NoSuchElementException("Không tìm thấy báo cáo $reportId")
        }
        doc.toAdminReport() ?: throw IllegalStateException("Không thể đọc dữ liệu báo cáo")
    }

    override suspend fun moderateReport(decision: ModerateReportDecision): Result<Unit> = adminRunCatching {
        val validationErr = decision.validate()
        if (validationErr != null) {
            throw IllegalArgumentException(validationErr)
        }
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_MODERATE,
                body = AdminModerateRequest(
                    reportId = decision.reportId,
                    action = decision.action.name,
                ),
            ).body<AdminModerateResponse>()
        }
    }

    override suspend fun getTargetSummary(targetPath: String): Result<Map<String, Any?>> = adminRunCatching {
        if (targetPath.isBlank()) return@adminRunCatching emptyMap()
        val doc = firestore.document(targetPath).get().await()
        if (!doc.exists()) return@adminRunCatching emptyMap()
        doc.data.orEmpty()
    }

    private fun DocumentSnapshot.toAdminReport(): AdminReport? {
        val id = id
        val reporterId = getString("reporterId").orEmpty()
        val rawType = getString("targetType")
        val targetType = ReportTargetType.fromString(rawType)
        val targetPath = getString("targetPath").orEmpty()
        val reason = getString("reason").orEmpty()
        val detail = getString("detail").orEmpty()
        val rawStatus = getString("status")
        val status = ReportStatus.fromString(rawStatus)
        val rawAction = getString("action")
        val action = ReportAction.fromString(rawAction)
        val handledBy = getString("handledBy")
        val handledAt = timestampMillis("handledAt")
        val createdAt = timestampMillis("createdAt") ?: 0L

        return AdminReport(
            id = id,
            reporterId = reporterId,
            targetType = targetType,
            targetPath = targetPath,
            reason = reason,
            detail = detail,
            status = status,
            action = action,
            handledBy = handledBy,
            handledAt = handledAt,
            createdAt = createdAt,
        )
    }

    companion object {
        private const val FUNCTION_MODERATE = "admin-moderate-report"
    }

    private fun DocumentSnapshot.timestampMillis(field: String): Long? = when (val value = get(field)) {
        is com.google.firebase.Timestamp -> value.toDate().time
        is String -> adminRunCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        else -> null
    }
}

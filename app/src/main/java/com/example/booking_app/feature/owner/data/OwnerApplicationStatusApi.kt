package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMetadata
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusRepository
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusSnapshot
import com.example.booking_app.feature.owner.domain.OwnerApplicationReviewStatus
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerRole
import com.example.booking_app.feature.owner.domain.OwnerStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject

@Serializable
data class OwnerApplicationStatusResponse(
    val ownerStatus: String? = null,
    val role: String? = null,
    val application: OwnerApplicationStatusResponseApplication? = null,
)

@Serializable
data class OwnerApplicationStatusResponseApplication(
    val id: String,
    val status: String? = null,
    val submittedAt: String? = null,
    val rejectReason: String? = null,
    val venueDraft: OwnerApplicationStatusVenueDraft? = null,
    val representativeName: String? = null,
    val representativePhone: String? = null,
    val documentPaths: List<String>? = null,
)

@Serializable
data class OwnerApplicationStatusVenueDraft(
    val name: String,
    val address: String,
    val districtId: String,
    val location: OwnerApplicationStatusLocation,
    val phone: String,
    val courtCount: Int? = null,
    val openTime: String,
    val closeTime: String,
    val photoPaths: List<String> = emptyList(),
)

@Serializable
data class OwnerApplicationStatusLocation(val latitude: Double, val longitude: Double)

interface OwnerApplicationStatusApi {
    suspend fun fetch(): OwnerApplicationStatusResponse
}

class SupabaseOwnerApplicationStatusApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerApplicationStatusApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(): OwnerApplicationStatusResponse = functionErrors.invokeWithAuthRetry {
        val response = try {
            withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FUNCTION_NAME) }
        } catch (e: TimeoutCancellationException) {
            throw AppError.Timeout
        }
        json.decodeFromString<OwnerApplicationStatusResponse>(response.bodyAsText())
    }

    private companion object {
        const val FUNCTION_NAME = "get-owner-application-status"
    }
}

class OwnerApplicationStatusRepositoryImpl @Inject constructor(
    private val api: OwnerApplicationStatusApi,
) : OwnerApplicationStatusRepository {
    override suspend fun getAuthoritativeStatus(): OwnerApplicationStatusSnapshot {
        val response = api.fetch()
        val status = OwnerStatus.fromWire(response.ownerStatus)
        val application = response.application?.let {
            val reviewStatus = OwnerApplicationReviewStatus.fromWire(it.status ?: response.ownerStatus)
                ?: throw IllegalStateException("Trạng thái hồ sơ không hợp lệ")
            val rejectedDraft = if (reviewStatus == OwnerApplicationReviewStatus.REJECTED) {
                val venue = it.venueDraft ?: throw IllegalStateException("Thiếu dữ liệu hồ sơ bị từ chối")
                OwnerApplicationDraft(
                    venue = com.example.booking_app.feature.owner.domain.VenueDraft(
                        name = venue.name,
                        address = venue.address,
                        districtId = venue.districtId,
                        latitude = venue.location.latitude,
                        longitude = venue.location.longitude,
                        phone = venue.phone,
                        courtCount = venue.courtCount,
                        openTime = venue.openTime,
                        closeTime = venue.closeTime,
                        photoPaths = venue.photoPaths,
                    ),
                    representativeName = it.representativeName.orEmpty(),
                    representativePhone = it.representativePhone.orEmpty(),
                    documentPaths = it.documentPaths.orEmpty(),
                )
            } else null
            OwnerApplicationStatusMetadata(
                id = it.id,
                status = reviewStatus,
                submittedAt = it.submittedAt?.let(Instant::parse),
                rejectReason = it.rejectReason,
                rejectedDraft = rejectedDraft,
                documentPaths = it.documentPaths.orEmpty(),
            )
        }
        return OwnerApplicationStatusSnapshot(
            ownerStatus = status,
            role = OwnerRole.fromWire(response.role),
            application = application,
        )
    }
}

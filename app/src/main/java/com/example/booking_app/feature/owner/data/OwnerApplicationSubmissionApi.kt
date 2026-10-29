package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class OwnerApplicationSubmitRequest(
    val applicationId: String,
    val venueDraft: OwnerApplicationSubmitVenue,
    val representativeName: String,
    val representativePhone: String,
    val documentPaths: List<String>,
)

@Serializable
data class OwnerApplicationSubmitVenue(
    val name: String,
    val address: String,
    val districtId: String,
    val location: OwnerApplicationSubmitLocation,
    val phone: String,
    val courtCount: Int,
    val openTime: String,
    val closeTime: String,
    val photoPaths: List<String>,
)

@Serializable
data class OwnerApplicationSubmitLocation(val latitude: Double, val longitude: Double)

@Serializable
data class OwnerApplicationSubmitResponse(val applicationId: String)

interface OwnerApplicationSubmitApi {
    suspend fun submit(applicationId: String, draft: OwnerApplicationDraft): String
}

class SupabaseOwnerApplicationSubmitApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerApplicationSubmitApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun submit(applicationId: String, draft: OwnerApplicationDraft): String =
        functionErrors.invokeWithAuthRetry {
            val latitude = requireNotNull(draft.venue.latitude)
            val longitude = requireNotNull(draft.venue.longitude)
            val courtCount = requireNotNull(draft.venue.courtCount)
            val request = OwnerApplicationSubmitRequest(
                applicationId = applicationId,
                venueDraft = OwnerApplicationSubmitVenue(
                    name = draft.venue.name,
                    address = draft.venue.address,
                    districtId = draft.venue.districtId,
                    location = OwnerApplicationSubmitLocation(latitude, longitude),
                    phone = draft.venue.phone,
                    courtCount = courtCount,
                    openTime = draft.venue.openTime,
                    closeTime = draft.venue.closeTime,
                    photoPaths = draft.venue.photoPaths,
                ),
                representativeName = draft.representativeName,
                representativePhone = draft.representativePhone,
                documentPaths = draft.documentPaths,
            )
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) {
                    supabase.functions.invoke(FUNCTION_NAME, body = request)
                }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            json.decodeFromString<OwnerApplicationSubmitResponse>(response.bodyAsText()).applicationId
        }

    private companion object {
        const val FUNCTION_NAME = "owner-application-submit"
    }
}

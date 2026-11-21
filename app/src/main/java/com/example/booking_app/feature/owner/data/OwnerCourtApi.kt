package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable
private data class CourtWire(val name: String, val surfaceType: String, val isActive: Boolean, val sortOrder: Int)
@Serializable
private data class CourtMutationRequest(
    val venueId: String, val action: String, val courtId: String,
    val court: CourtWire? = null, val secondCourtId: String? = null,
    val firstOrder: Int? = null, val secondOrder: Int? = null,
)
@Serializable
private data class CourtMutationResponse(val venueId: String, val courtId: String, val courtCount: Int)

class OwnerCourtApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) {
    suspend fun save(venueId: String, courtId: String, fields: CourtFields, create: Boolean): String =
        invoke(CourtMutationRequest(venueId, if (create) "CREATE" else "UPDATE", courtId,
            CourtWire(fields.name, fields.surfaceType, fields.isActive, fields.sortOrder)))

    suspend fun reorder(venueId: String, first: OwnerCourt, second: OwnerCourt) {
        invoke(CourtMutationRequest(venueId, "REORDER", first.id, secondCourtId = second.id,
            firstOrder = first.sortOrder, secondOrder = second.sortOrder))
    }

    private suspend fun invoke(request: CourtMutationRequest): String {
        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke("owner-manage-court", body = request)
        }.body<CourtMutationResponse>()
        check(response.venueId == request.venueId && response.courtId == request.courtId && response.courtCount >= 0) {
            "Chưa xác nhận được sân. Hãy tải lại trước khi thử lại."
        }
        return response.courtId
    }
}

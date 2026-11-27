package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.Serializable
import com.example.booking_app.feature.owner.domain.PriceRuleInput
import io.ktor.client.call.body
import javax.inject.Inject

@Serializable
data class SyncVenuePriceRequest(val venueId: String)

@Serializable
data class PriceRuleWire(val daysOfWeek: List<Int>, val startTime: String, val endTime: String, val pricePerHour: Long, val label: String)
@Serializable
data class PriceMutationRequest(val venueId: String, val action: String, val ruleId: String? = null, val rule: PriceRuleWire? = null)
@Serializable
private data class PriceMutationResponse(val venueId: String, val ruleId: String? = null, val minPricePerHour: Long? = null)

interface SyncVenuePriceApi {
    suspend fun syncVenuePrice(venueId: String): Result<Unit>
    suspend fun mutate(venueId: String, action: String, ruleId: String? = null, rule: PriceRuleInput? = null): Result<String?>
}

class SupabaseSyncVenuePriceApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors
) : SyncVenuePriceApi {
    override suspend fun mutate(venueId: String, action: String, ruleId: String?, rule: PriceRuleInput?): Result<String?> = runCatching {
        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(FUNCTION_NAME, body = PriceMutationRequest(venueId, action, ruleId,
                rule?.let { PriceRuleWire(it.daysOfWeek, it.startTime, it.endTime, it.pricePerHour, it.label) }))
        }.body<PriceMutationResponse>()
        check(response.venueId == venueId) { "Kết quả không khớp cơ sở. Hãy tải lại." }
        if (action == "CREATE") check(!response.ruleId.isNullOrBlank()) { "Chưa xác nhận được khung giá. Hãy tải lại." }
        response.ruleId
    }
    override suspend fun syncVenuePrice(venueId: String): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(FUNCTION_NAME, body = SyncVenuePriceRequest(venueId))
        }
    }

    companion object {
        const val FUNCTION_NAME = "sync-venue-price"
    }
}

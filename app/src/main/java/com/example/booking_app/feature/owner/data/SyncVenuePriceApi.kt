package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class SyncVenuePriceRequest(val venueId: String)

interface SyncVenuePriceApi {
    suspend fun syncVenuePrice(venueId: String): Result<Unit>
}

class SupabaseSyncVenuePriceApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors
) : SyncVenuePriceApi {
    override suspend fun syncVenuePrice(venueId: String): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(FUNCTION_NAME, body = SyncVenuePriceRequest(venueId))
        }
    }

    companion object {
        const val FUNCTION_NAME = "sync-venue-price"
    }
}

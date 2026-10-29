package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class OwnerDistrict(val id: String, val name: String, val sortOrder: Int = 0)

@Serializable
data class OwnerDistrictCatalogResponse(val districts: List<OwnerDistrict> = emptyList())

interface OwnerDistrictCatalogApi {
    suspend fun fetch(): List<OwnerDistrict>
}

class SupabaseOwnerDistrictCatalogApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerDistrictCatalogApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(): List<OwnerDistrict> = functionErrors.invokeWithAuthRetry {
        val response = try {
            withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FUNCTION_NAME) }
        } catch (error: TimeoutCancellationException) {
            throw AppError.Timeout
        }
        json.decodeFromString<OwnerDistrictCatalogResponse>(response.bodyAsText()).districts
    }

    private companion object {
        const val FUNCTION_NAME = "owner-district-catalog"
    }
}

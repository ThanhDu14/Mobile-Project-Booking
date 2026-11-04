package com.example.booking_app.feature.account.data

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

/** Các Edge Function của spec 011 (contracts/edge-functions-account.md). Ném `AppError` hoặc `AccountError`. */
interface AccountFunctionsApi {
    /** `sync-account-email`: `true` khi server vừa ghi email mới. */
    suspend fun syncAccountEmail(): Boolean
}

@Serializable
internal data class SyncEmailResponse(val updated: Boolean)

@Serializable
internal class EmptyRequest

class SupabaseAccountFunctionsApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : AccountFunctionsApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun syncAccountEmail(): Boolean = functionErrors.invokeWithAuthRetry {
        val response = try {
            withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(SYNC_EMAIL, body = EmptyRequest()) }
        } catch (e: TimeoutCancellationException) {
            throw AppError.Timeout
        }
        json.decodeFromString<SyncEmailResponse>(response.bodyAsText()).updated
    }

    private companion object {
        const val SYNC_EMAIL = "sync-account-email"
    }
}

package com.example.booking_app.feature.auth.data

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

/** Body của Edge Function `on-signup` (contracts/edge-function-on-signup.md §1). */
@Serializable
data class OnSignupRequest(val displayName: String?, val termsAccepted: Boolean)

/** `status`: `CREATED` hoặc `ALREADY_INITIALIZED` (§3). */
@Serializable
data class OnSignupResponse(val status: String, val appRole: String)

/** Ném `AppError`; lỗi `400` mang `FunctionHttpException` trong `AppError.Unknown`. */
interface OnSignupApi {
    suspend fun onSignup(request: OnSignupRequest): OnSignupResponse
}

class SupabaseOnSignupApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OnSignupApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun onSignup(request: OnSignupRequest): OnSignupResponse =
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FUNCTION_NAME, body = request) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            json.decodeFromString<OnSignupResponse>(response.bodyAsText())
        }

    private companion object {
        const val FUNCTION_NAME = "on-signup"
    }
}

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
data class OwnerDocumentDownloadRequest(val applicationId: String, val path: String)

@Serializable
data class OwnerDocumentDownloadResponse(val signedUrl: String, val expiresInSeconds: Int)

interface OwnerDocumentDownloadApi {
    suspend fun requestFreshUrl(applicationId: String, path: String): String
}

/** A URL is fetched for each user action and is never cached or put into saved state. */
class SupabaseOwnerDocumentDownloadApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerDocumentDownloadApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requestFreshUrl(applicationId: String, path: String): String =
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) {
                    supabase.functions.invoke(
                        FUNCTION_NAME,
                        body = OwnerDocumentDownloadRequest(applicationId, path),
                    )
                }
            } catch (error: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            json.decodeFromString<OwnerDocumentDownloadResponse>(response.bodyAsText())
                .signedUrl
                .also { require(it.startsWith("https://")) }
        }

    private companion object {
        const val FUNCTION_NAME = "owner-document-download"
    }
}

package com.example.booking_app.feature.community.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.community.data.dto.CommunityFunctionJson
import com.example.booking_app.feature.community.data.dto.GroupPostRequest
import com.example.booking_app.feature.community.data.dto.GroupPostResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/** Edge Function `group-post` (contracts/edge-functions-community.md mục 2). */
interface GroupPostApi {
    suspend fun invoke(request: GroupPostRequest): GroupPostResponse
}

class SupabaseGroupPostApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : GroupPostApi {

    override suspend fun invoke(request: GroupPostRequest): GroupPostResponse {
        val body = CommunityFunctionJson.encodeToJsonElement(request)
        return functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FN_GROUP_POST, body = body) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            CommunityFunctionJson.decodeFromString<GroupPostResponse>(response.bodyAsText())
        }
    }

    private companion object {
        const val FN_GROUP_POST = "group-post"
    }
}

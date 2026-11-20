package com.example.booking_app.feature.community.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.community.data.dto.CommunityFunctionJson
import com.example.booking_app.feature.community.data.dto.GroupManageRequest
import com.example.booking_app.feature.community.data.dto.GroupManageResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/**
 * Edge Function `group-manage` (contracts/edge-functions-community.md mục 1). Ném lỗi như [FunctionErrors]: `401` đã
 * làm mới token và thử lại một lần; mã nghiệp vụ nằm trong `AppError.Unknown(FunctionHttpException)`, đổi bằng
 * `toCommunityError()`.
 */
interface GroupManageApi {
    suspend fun invoke(request: GroupManageRequest): GroupManageResponse
}

class SupabaseGroupManageApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : GroupManageApi {

    override suspend fun invoke(request: GroupManageRequest): GroupManageResponse {
        // Tự mã hóa thành JsonElement để trường null không bị gửi đi (cấu hình Json của supabase-kt có thể ghi null).
        val body = CommunityFunctionJson.encodeToJsonElement(request)
        return functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FN_GROUP_MANAGE, body = body) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            CommunityFunctionJson.decodeFromString<GroupManageResponse>(response.bodyAsText())
        }
    }

    private companion object {
        const val FN_GROUP_MANAGE = "group-manage"
    }
}

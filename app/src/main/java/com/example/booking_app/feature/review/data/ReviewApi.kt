package com.example.booking_app.feature.review.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.review.data.dto.CreateReviewRequest
import com.example.booking_app.feature.review.data.dto.ReviewFunctionJson
import com.example.booking_app.feature.review.data.dto.ReviewWriteResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Edge Function `review` (contracts/edge-function-review.md). Ném `AppError` như [FunctionErrors]:
 * `401` đã được làm mới token và thử lại một lần; mã lỗi nghiệp vụ nằm trong `AppError.Unknown`
 * (đổi bằng [toReviewError]).
 */
interface ReviewApi {
    suspend fun create(request: CreateReviewRequest): ReviewWriteResponse
}

class SupabaseReviewApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : ReviewApi {

    override suspend fun create(request: CreateReviewRequest): ReviewWriteResponse =
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FUNCTION_NAME, body = request) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            ReviewFunctionJson.decodeFromString<ReviewWriteResponse>(response.bodyAsText())
        }

    private companion object {
        const val FUNCTION_NAME = "review"
    }
}

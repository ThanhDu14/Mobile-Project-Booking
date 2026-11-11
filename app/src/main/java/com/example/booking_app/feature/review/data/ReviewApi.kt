package com.example.booking_app.feature.review.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.review.data.dto.CreateReviewRequest
import com.example.booking_app.feature.review.data.dto.DeleteReviewRequest
import com.example.booking_app.feature.review.data.dto.DeleteReviewResponse
import com.example.booking_app.feature.review.data.dto.DiscardPhotosRequest
import com.example.booking_app.feature.review.data.dto.DiscardPhotosResponse
import com.example.booking_app.feature.review.data.dto.ReplyReviewRequest
import com.example.booking_app.feature.review.data.dto.ReplyReviewResponse
import com.example.booking_app.feature.review.data.dto.ReviewFunctionJson
import com.example.booking_app.feature.review.data.dto.ReviewWriteResponse
import com.example.booking_app.feature.review.data.dto.UpdateReviewRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import javax.inject.Inject

/**
 * Edge Function `review` (contracts/edge-function-review.md). Ném `AppError` như [FunctionErrors]:
 * `401` đã được làm mới token và thử lại một lần; mã lỗi nghiệp vụ nằm trong `AppError.Unknown`
 * (đổi bằng [toReviewError]).
 */
interface ReviewApi {
    suspend fun create(request: CreateReviewRequest): ReviewWriteResponse
    suspend fun update(request: UpdateReviewRequest): ReviewWriteResponse
    suspend fun delete(request: DeleteReviewRequest): DeleteReviewResponse
    suspend fun reply(request: ReplyReviewRequest): ReplyReviewResponse
    suspend fun discardPhotos(request: DiscardPhotosRequest): DiscardPhotosResponse
}

class SupabaseReviewApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : ReviewApi {

    override suspend fun create(request: CreateReviewRequest): ReviewWriteResponse = invoke(request, serializer())

    override suspend fun update(request: UpdateReviewRequest): ReviewWriteResponse = invoke(request, serializer())

    override suspend fun delete(request: DeleteReviewRequest): DeleteReviewResponse = invoke(request, serializer())

    override suspend fun reply(request: ReplyReviewRequest): ReplyReviewResponse = invoke(request, serializer())

    override suspend fun discardPhotos(request: DiscardPhotosRequest): DiscardPhotosResponse =
        invoke(request, serializer())

    private suspend inline fun <reified Req : Any, Res> invoke(request: Req, responseSerializer: KSerializer<Res>): Res =
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FUNCTION_NAME, body = request) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            ReviewFunctionJson.decodeFromString(responseSerializer, response.bodyAsText())
        }

    private companion object {
        const val FUNCTION_NAME = "review"
    }
}

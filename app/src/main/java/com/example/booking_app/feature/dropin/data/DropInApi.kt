package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.dropin.data.dto.AddPartyRequest
import com.example.booking_app.feature.dropin.data.dto.CancelDropInRequest
import com.example.booking_app.feature.dropin.data.dto.CancelDropInResponse
import com.example.booking_app.feature.dropin.data.dto.DropInFunctionJson
import com.example.booking_app.feature.dropin.data.dto.RegisterDropInRequest
import com.example.booking_app.feature.dropin.data.dto.RegisterDropInResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Edge Function `register-drop-in`, `cancel-drop-in` (contracts/edge-functions-dropin.md). Ném lỗi
 * như [FunctionErrors]: `401` đã được làm mới token và thử lại một lần; mã nghiệp vụ nằm trong
 * `AppError.Unknown(FunctionHttpException)`, đổi bằng `toDropInError()`.
 */
interface DropInApi {
    suspend fun register(request: RegisterDropInRequest): RegisterDropInResponse

    /** `register-drop-in` với `action: "ADD_PARTY"` (FR-008a). */
    suspend fun addParty(request: AddPartyRequest): RegisterDropInResponse

    suspend fun cancel(request: CancelDropInRequest): CancelDropInResponse
}

class SupabaseDropInApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : DropInApi {

    override suspend fun register(request: RegisterDropInRequest): RegisterDropInResponse =
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FN_REGISTER, body = request) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            DropInFunctionJson.decodeFromString<RegisterDropInResponse>(response.bodyAsText())
        }

    override suspend fun addParty(request: AddPartyRequest): RegisterDropInResponse =
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FN_REGISTER, body = request) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            DropInFunctionJson.decodeFromString<RegisterDropInResponse>(response.bodyAsText())
        }

    override suspend fun cancel(request: CancelDropInRequest): CancelDropInResponse =
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FN_CANCEL, body = request) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            DropInFunctionJson.decodeFromString<CancelDropInResponse>(response.bodyAsText())
        }

    private companion object {
        const val FN_REGISTER = "register-drop-in"
        const val FN_CANCEL = "cancel-drop-in"
    }
}

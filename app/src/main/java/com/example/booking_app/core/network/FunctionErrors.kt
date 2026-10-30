package com.example.booking_app.core.network

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val CODE_ACCOUNT_LOCKED = "ACCOUNT_LOCKED"
private const val CODE_RATE_LIMITED = "RATE_LIMITED"
private const val CODE_ACCOUNT_DELETING = "ACCOUNT_DELETING"

/** Lỗi HTTP của Edge Function, tách khỏi kiểu của supabase-kt để đọc body và test được. */
class FunctionHttpException(val status: Int, val body: String?) : Exception("Edge Function HTTP $status")

/** Đọc `error.code` trong body `{ "error": { "code", "message" } }` (contract `_shared/errors.ts`). */
fun parseFunctionErrorCode(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        val error = Json.parseToJsonElement(body).jsonObject["error"] as? JsonObject
        error?.get("code")?.jsonPrimitive?.content
    }.getOrNull()
}

/**
 * Bọc mọi lời gọi Edge Function:
 * - `401`: làm mới ID token rồi thử lại đúng một lần;
 * - `403 ACCOUNT_LOCKED`: báo [AccountLockedBus] để `SessionManager` đăng xuất, ném [AppError.Locked];
 * - `403 ACCOUNT_DELETING` (spec 011): báo [AccountDeletedBus], ném [AppError.AccountDeleted];
 * - lỗi mạng và các mã khác đổi thành [AppError].
 */
@Singleton
class FunctionErrors @Inject constructor(
    private val idTokenProvider: IdTokenProvider,
    private val lockedBus: AccountLockedBus,
    private val deletedBus: AccountDeletedBus,
) {
    suspend fun <T> invokeWithAuthRetry(call: suspend () -> T): T {
        try {
            return call()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val failure = e.toFunctionHttpException() ?: throw e.toAppError()
            if (failure.status != 401) throw map(failure)
        }
        idTokenProvider.getToken(forceRefresh = true)
        try {
            return call()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val failure = e.toFunctionHttpException() ?: throw e.toAppError()
            throw if (failure.status == 401) AppError.NotSignedIn else map(failure)
        }
    }

    private fun map(failure: FunctionHttpException): AppError {
        val code = parseFunctionErrorCode(failure.body)
        return when {
            failure.status == 403 && code == CODE_ACCOUNT_LOCKED -> {
                lockedBus.publish()
                AppError.Locked
            }
            failure.status == 403 && code == CODE_ACCOUNT_DELETING -> {
                deletedBus.publish()
                AppError.AccountDeleted
            }
            failure.status == 429 || code == CODE_RATE_LIMITED -> AppError.RateLimited
            else -> AppError.Unknown(failure)
        }
    }

    private suspend fun Throwable.toFunctionHttpException(): FunctionHttpException? = when (this) {
        is FunctionHttpException -> this
        is RestException -> FunctionHttpException(statusCode, runCatching { response.bodyAsText() }.getOrNull())
        else -> null
    }
}

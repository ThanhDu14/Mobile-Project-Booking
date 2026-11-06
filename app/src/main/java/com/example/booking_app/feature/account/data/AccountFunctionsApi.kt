package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.network.parseFunctionErrorCode
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.DeletionImpact
import com.example.booking_app.feature.account.domain.model.ImpactBooking
import com.example.booking_app.feature.account.domain.model.ImpactDropIn
import com.example.booking_app.feature.account.domain.model.OwnerImpact
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import javax.inject.Inject

/** Các Edge Function của spec 011 (contracts/edge-functions-account.md). Ném `AppError` hoặc `AccountError`. */
interface AccountFunctionsApi {
    /** `sync-account-email`: `true` khi server vừa ghi email mới. */
    suspend fun syncAccountEmail(): Boolean

    /** `delete-account` action `preview` (chỉ đọc). */
    suspend fun previewDeletion(): DeletionImpact

    /**
     * `delete-account` action `confirm`. Trả bình thường khi `200 DELETED`, `202 CLEANUP_SCHEDULED` hoặc
     * `403 ACCOUNT_DELETING` (đã chốt ở lần gọi trước); lỗi là `AccountError` theo §1.4.
     */
    suspend fun confirmDeletion(impactVersion: String, confirmText: String)
}

@Serializable
internal data class SyncEmailResponse(val updated: Boolean)

@Serializable
internal class EmptyRequest

@Serializable
internal data class DeleteAccountRequest(
    val action: String,
    val impactVersion: String? = null,
    val confirmText: String? = null,
)

@Serializable
internal data class PreviewDto(
    val impactVersion: String,
    val walletBalance: Long = 0,
    val myBookings: List<BookingDto> = emptyList(),
    val myDropIns: List<DropInDto> = emptyList(),
    val waitlistCount: Int = 0,
    val ownerImpact: OwnerImpactDto? = null,
)

@Serializable
internal data class BookingDto(
    val bookingId: String,
    val venueName: String = "",
    val startAt: String? = null,
    val status: String = "",
    val paidAmount: Long = 0,
    val refundAmount: Long = 0,
)

@Serializable
internal data class DropInDto(
    val sessionId: String,
    val venueName: String = "",
    val startAt: String? = null,
    val paidAmount: Long = 0,
    val refundAmount: Long = 0,
)

@Serializable
internal data class OwnerImpactDto(
    val venueCount: Int,
    val guestBookingCount: Int,
    val guestDropInCount: Int,
    val guestCount: Int,
)

@Serializable
internal data class ConfirmResponse(val status: String)

/** Phần thuần (không gọi mạng) của `delete-account`, tách ra để test (T085). */
object DeleteAccountResponses {
    const val PREVIEW_TIMEOUT_MS = DEFAULT_TIMEOUT_MS
    const val CONFIRM_TIMEOUT_MS = 120_000L

    private val json = Json { ignoreUnknownKeys = true }

    fun parsePreview(body: String): DeletionImpact = json.decodeFromString<PreviewDto>(body).toDomain()

    /** `200` và `202` đều là thành công; body lạ thì ném để tầng trên báo lỗi. */
    fun checkConfirmBody(body: String) {
        val status = json.decodeFromString<ConfirmResponse>(body).status
        check(status == "DELETED" || status == "CLEANUP_SCHEDULED") { "Unexpected status $status" }
    }

    /** `null` nghĩa là coi như thành công (`ACCOUNT_DELETING`). */
    fun mapConfirmFailure(error: Throwable): Exception? = when (error) {
        AppError.AccountDeleted -> null
        AppError.Network, AppError.Timeout -> AccountError.Network
        is AppError.Unknown -> (error.cause as? FunctionHttpException)?.let(::mapHttp) ?: AccountError.Unknown(error)
        is AccountError -> error
        is AppError -> error
        else -> AccountError.Unknown(error)
    }

    private fun mapHttp(e: FunctionHttpException): Exception = when (parseFunctionErrorCode(e.body)) {
        "REAUTH_REQUIRED" -> AccountError.ReauthRequired
        "ADMIN_SELF_DELETE" -> AccountError.AdminSelfDelete
        "DELETE_TOO_LARGE" -> AccountError.DeleteTooLarge
        "CONFIRM_TEXT_MISMATCH" -> AccountError.DeleteConfirmMismatch
        "IMPACT_CHANGED" -> previewIn(e.body)?.let { AccountError.ImpactChanged(it) } ?: AccountError.Unknown(e)
        else -> AccountError.Unknown(e)
    }

    private fun previewIn(body: String?): DeletionImpact? = runCatching {
        val preview = (Json.parseToJsonElement(body!!).jsonObject["error"] as JsonObject)["preview"]!!
        json.decodeFromJsonElement(PreviewDto.serializer(), preview).toDomain()
    }.getOrNull()

    private fun instant(value: String?): Instant = value?.let(Instant::parse) ?: Instant.EPOCH

    private fun PreviewDto.toDomain() = DeletionImpact(
        impactVersion = impactVersion,
        myBookings = myBookings.map {
            ImpactBooking(it.bookingId, it.venueName, instant(it.startAt), it.status, it.paidAmount, it.refundAmount)
        },
        myDropIns = myDropIns.map {
            ImpactDropIn(it.sessionId, it.venueName, instant(it.startAt), it.paidAmount, it.refundAmount)
        },
        waitlistCount = waitlistCount,
        ownerImpact = ownerImpact?.let {
            OwnerImpact(it.venueCount, it.guestBookingCount, it.guestDropInCount, it.guestCount)
        },
        walletBalance = walletBalance,
    )
}

class SupabaseAccountFunctionsApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : AccountFunctionsApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun syncAccountEmail(): Boolean = accountCall {
        json.decodeFromString<SyncEmailResponse>(invoke(SYNC_EMAIL, EmptyRequest(), DEFAULT_TIMEOUT_MS)).updated
    }

    override suspend fun previewDeletion(): DeletionImpact = accountCall {
        DeleteAccountResponses.parsePreview(
            invoke(DELETE_ACCOUNT, DeleteAccountRequest("preview"), DeleteAccountResponses.PREVIEW_TIMEOUT_MS),
        )
    }

    override suspend fun confirmDeletion(impactVersion: String, confirmText: String) {
        val body = try {
            invoke(
                DELETE_ACCOUNT,
                DeleteAccountRequest("confirm", impactVersion, confirmText),
                DeleteAccountResponses.CONFIRM_TIMEOUT_MS,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DeleteAccountResponses.mapConfirmFailure(e) ?: return
        }
        DeleteAccountResponses.checkConfirmBody(body)
    }

    private suspend inline fun <reified T : Any> invoke(name: String, body: T, timeoutMs: Long): String =
        functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(timeoutMs) { supabase.functions.invoke(name, body = body) }
            } catch (e: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            response.bodyAsText()
        }

    private companion object {
        const val SYNC_EMAIL = "sync-account-email"
        const val DELETE_ACCOUNT = "delete-account"
    }
}

@file:OptIn(ExperimentalSerializationApi::class)

package com.example.booking_app.feature.dropin.data.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Body và response của `register-drop-in`, `cancel-drop-in` (contracts/edge-functions-dropin.md). */
val DropInFunctionJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

// ---------- register-drop-in ----------

@Serializable
data class RegisterDropInRequest(
    val sessionId: String,
    val partySize: Int,
    val paymentMethod: String,
    val requestId: String,
    /** Spec 081: `true` để vào hàng chờ; `false` (mặc định) không gửi để giữ nguyên request của spec 080. */
    val joinWaitlist: Boolean = false,
)

/** `action: "ADD_PARTY"` (FR-008a): thêm [additional] người vào đăng ký đang có; response như đăng ký. */
@Serializable
data class AddPartyRequest(
    val sessionId: String,
    val additional: Int,
    val requestId: String,
    @EncodeDefault val action: String = "ADD_PARTY",
)

/** `registration` trong response; không có `qrToken` (app đọc token qua Firestore). */
@Serializable
data class RegistrationResultDto(
    val sessionId: String? = null,
    val status: String? = null,
    val partySize: Int? = null,
    val paymentMethod: String? = null,
    val paymentStatus: String? = null,
    val pricePerPerson: Long? = null,
    val amount: Long? = null,
    val refundAmount: Long? = null,
    val ticketCode: String? = null,
    /** ISO-8601. */
    val registeredAt: String? = null,
    val cancelledAt: String? = null,
    /** Spec 081: lượt chờ. */
    val waitlistedAt: String? = null,
    val waitlistTicket: Int? = null,
    val promotedAt: String? = null,
    val waitlistEndReason: String? = null,
)

/** `waitlist` trong response vào hàng chờ: vị trí 1-based và tổng số lượt đang chờ (spec 081 contract mục 1). */
@Serializable
data class WaitlistDto(val position: Int? = null, val total: Int? = null)

@Serializable
data class SessionCountDto(
    val registeredCount: Int? = null,
    val capacity: Int? = null,
    val status: String? = null,
)

@Serializable
data class RegisterDropInResponse(
    val registration: RegistrationResultDto,
    val session: SessionCountDto? = null,
    /** Spec 081: có khi `joinWaitlist: true` (contract mục 1). */
    val waitlist: WaitlistDto? = null,
)

// ---------- cancel-drop-in (spec 080 US4) ----------

@Serializable
data class CancelDropInRequest(
    val sessionId: String,
    val requestId: String,
    @EncodeDefault val dryRun: Boolean = false,
    /** Spec 081: app gửi `true` khi bấm "Rời hàng chờ"; lượt đã được đẩy lên thì server trả `ALREADY_PROMOTED`. */
    val expectWaitlisted: Boolean = false,
)

@Serializable
data class CancelQuoteDto(val refundAmount: Long? = null, val reason: String? = null)

@Serializable
data class CancelDropInResponse(
    val registration: RegistrationResultDto? = null,
    val quote: CancelQuoteDto? = null,
    val session: SessionCountDto? = null,
    /** Spec 081: số người được đẩy lên từ hàng chờ nhờ lượt hủy này (không có danh tính). */
    val promotedCount: Int? = null,
)

// ---------- Body lỗi: { "error": { "code", "message", "remaining"? } } ----------

data class FunctionErrorBody(
    val code: String?,
    val message: String?,
    val remaining: Int?,
    /** `PARTY_LIMIT` của ADD_PARTY. */
    val maxAdditional: Int? = null,
    /** `SESSION_FULL` (spec 081): hàng chờ còn mở hay đã đóng. */
    val waitlistOpen: Boolean? = null,
) {
    companion object {
        fun parse(body: String?): FunctionErrorBody? {
            if (body.isNullOrBlank()) return null
            return runCatching {
                val error = Json.parseToJsonElement(body).jsonObject["error"] as? JsonObject ?: return null
                FunctionErrorBody(
                    code = error["code"]?.jsonPrimitive?.content,
                    message = error["message"]?.jsonPrimitive?.content,
                    remaining = error["remaining"]?.jsonPrimitive?.longOrNull?.toInt(),
                    maxAdditional = error["maxAdditional"]?.jsonPrimitive?.longOrNull?.toInt(),
                    waitlistOpen = error["waitlistOpen"]?.jsonPrimitive?.booleanOrNull,
                )
            }.getOrNull()
        }
    }
}

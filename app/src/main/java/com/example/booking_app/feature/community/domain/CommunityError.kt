package com.example.booking_app.feature.community.domain

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Lỗi nghiệp vụ của spec 090, map từ `error.code` ở contracts/edge-functions-community.md mục 5. Repository trả
 * `Result.failure(CommunityError)` (cùng cách `DropInError` của spec 080).
 */
sealed class CommunityError(code: String) : Exception(code) {
    class InvalidArgument : CommunityError("INVALID_ARGUMENT")
    class ConfirmNameMismatch : CommunityError("CONFIRM_NAME_MISMATCH")
    class PhotoMissing : CommunityError("PHOTO_MISSING")
    class Unauthenticated : CommunityError("UNAUTHENTICATED")
    class AccountLocked : CommunityError("ACCOUNT_LOCKED")
    class NotMember : CommunityError("NOT_MEMBER")
    class Forbidden : CommunityError("FORBIDDEN")
    class HiddenBySystem : CommunityError("HIDDEN_BY_SYSTEM")
    class OwnPost : CommunityError("OWN_POST")
    class GroupNotFound : CommunityError("GROUP_NOT_FOUND")
    class PostNotFound : CommunityError("POST_NOT_FOUND")
    class RequestNotFound : CommunityError("REQUEST_NOT_FOUND")
    class MemberNotFound : CommunityError("MEMBER_NOT_FOUND")
    class OwnerLimitReached : CommunityError("OWNER_LIMIT_REACHED")
    class AlreadyMember : CommunityError("ALREADY_MEMBER")
    class AlreadyRequested : CommunityError("ALREADY_REQUESTED")

    /** Chủ nhóm rời nhóm: [soleMember] `true` khi chỉ còn một mình (gợi ý xóa nhóm thay vì chọn người nhận quyền). */
    data class OwnerMustTransfer(val soleMember: Boolean) : CommunityError("OWNER_MUST_TRANSFER")
    class EventPast : CommunityError("EVENT_PAST")
    class AlreadyReported : CommunityError("ALREADY_REPORTED")
    class TooManyPending : CommunityError("TOO_MANY_PENDING")
    class GroupLocked : CommunityError("GROUP_LOCKED")

    /** `503 BUSY`: hết lượt thử transaction; tầng data tự gửi lại cùng `requestId` tối đa 2 lần. */
    class Busy : CommunityError("BUSY")
    class Network : CommunityError("NETWORK")

    /** Tạo nhóm xong nhưng tải ảnh đại diện lỗi: nhóm vẫn có ([groupId]), cho thử lại hoặc bỏ ảnh (AC 1.3). */
    data class AvatarUploadFailed(val groupId: String) : CommunityError("AVATAR_UPLOAD_FAILED")

    /** Ảnh sau nén vẫn > 2 MB. */
    class ImageTooLarge : CommunityError("IMAGE_TOO_LARGE")

    /** Không đọc được ảnh. */
    class ImageUnreadable : CommunityError("IMAGE_UNREADABLE")

    /** Mã lạ (server thêm mà app chưa biết) hoặc lỗi không phân loại được. */
    data class Unknown(val code: String?) : CommunityError(code ?: "UNKNOWN")
}

/**
 * `error.code` (và `soleMember` của `OWNER_MUST_TRANSFER`) → [CommunityError]. Khi thiếu mã thì dựa vào [httpStatus]:
 * `503` là [CommunityError.Busy], `423` là [CommunityError.GroupLocked].
 */
fun communityErrorOf(code: String?, soleMember: Boolean = false, httpStatus: Int? = null): CommunityError = when (code) {
    "INVALID_ARGUMENT" -> CommunityError.InvalidArgument()
    "CONFIRM_NAME_MISMATCH" -> CommunityError.ConfirmNameMismatch()
    "PHOTO_MISSING" -> CommunityError.PhotoMissing()
    "UNAUTHENTICATED" -> CommunityError.Unauthenticated()
    "ACCOUNT_LOCKED" -> CommunityError.AccountLocked()
    "NOT_MEMBER" -> CommunityError.NotMember()
    "FORBIDDEN" -> CommunityError.Forbidden()
    "HIDDEN_BY_SYSTEM" -> CommunityError.HiddenBySystem()
    "OWN_POST" -> CommunityError.OwnPost()
    "GROUP_NOT_FOUND" -> CommunityError.GroupNotFound()
    "POST_NOT_FOUND" -> CommunityError.PostNotFound()
    "REQUEST_NOT_FOUND" -> CommunityError.RequestNotFound()
    "MEMBER_NOT_FOUND" -> CommunityError.MemberNotFound()
    "OWNER_LIMIT_REACHED" -> CommunityError.OwnerLimitReached()
    "ALREADY_MEMBER" -> CommunityError.AlreadyMember()
    "ALREADY_REQUESTED" -> CommunityError.AlreadyRequested()
    "OWNER_MUST_TRANSFER" -> CommunityError.OwnerMustTransfer(soleMember)
    "EVENT_PAST" -> CommunityError.EventPast()
    "ALREADY_REPORTED" -> CommunityError.AlreadyReported()
    "TOO_MANY_PENDING" -> CommunityError.TooManyPending()
    "GROUP_LOCKED" -> CommunityError.GroupLocked()
    "BUSY" -> CommunityError.Busy()
    null -> when (httpStatus) {
        503 -> CommunityError.Busy()
        423 -> CommunityError.GroupLocked()
        else -> CommunityError.Unknown(null)
    }
    else -> CommunityError.Unknown(code)
}

private val ErrorJson = Json { ignoreUnknownKeys = true }

/** Đổi lỗi sau `FunctionErrors` (hoặc lỗi Firestore/Storage đã qua `toAppError`) thành [CommunityError]. */
fun Throwable.toCommunityError(): CommunityError = when (this) {
    is CommunityError -> this
    AppError.Network, AppError.Timeout -> CommunityError.Network()
    AppError.Locked -> CommunityError.AccountLocked()
    AppError.NotSignedIn, AppError.SessionRevoked, AppError.AccountDeleted -> CommunityError.Unauthenticated()
    AppError.Conflict -> CommunityError.Busy()
    AppError.RateLimited -> CommunityError.Unknown("RATE_LIMITED")
    is AppError.Unknown -> when (val c = cause) {
        is FunctionHttpException -> c.toCommunityError()
        is IOException -> CommunityError.Network()
        else -> CommunityError.Unknown(null)
    }
    is FunctionHttpException -> toCommunityError()
    is IOException -> CommunityError.Network()
    else -> CommunityError.Unknown(null)
}

private fun FunctionHttpException.toCommunityError(): CommunityError {
    val error = runCatching { ErrorJson.parseToJsonElement(body.orEmpty()).jsonObject["error"] as? JsonObject }.getOrNull()
    val code = error?.get("code")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
    val soleMember = error?.get("soleMember")?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } ?: false
    return communityErrorOf(code, soleMember, status)
}

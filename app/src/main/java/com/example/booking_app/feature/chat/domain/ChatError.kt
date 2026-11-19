package com.example.booking_app.feature.chat.domain

import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.network.parseFunctionErrorCode
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import io.github.jan.supabase.exceptions.HttpRequestException
import java.io.IOException

/**
 * Lỗi nghiệp vụ của spec 091, ánh xạ đủ mọi mã ở `contracts/edge-functions-chat.md` mục 5
 * (cùng kiểu với `CommunityError` của spec 090).
 */
sealed class ChatError(val code: String) : Exception(code) {
    class InvalidArgument : ChatError("INVALID_ARGUMENT")
    class PhotoMissing : ChatError("PHOTO_MISSING")
    class Unauthenticated : ChatError("UNAUTHENTICATED")
    class AccountLocked : ChatError("ACCOUNT_LOCKED")
    class NotParticipant : ChatError("NOT_PARTICIPANT")
    class Blocked : ChatError("BLOCKED")
    class Forbidden : ChatError("FORBIDDEN")
    class OwnVenue : ChatError("OWN_VENUE")
    class OwnMessage : ChatError("OWN_MESSAGE")
    class ChatNotFound : ChatError("CHAT_NOT_FOUND")
    class MessageNotFound : ChatError("MESSAGE_NOT_FOUND")
    class ChatReadOnly : ChatError("CHAT_READ_ONLY")
    class VenueInactive : ChatError("VENUE_INACTIVE")
    class OwnerNotApproved : ChatError("OWNER_NOT_APPROVED")
    class RecallWindowPassed : ChatError("RECALL_WINDOW_PASSED")
    class AlreadyReported : ChatError("ALREADY_REPORTED")
    class GroupLocked : ChatError("GROUP_LOCKED")
    class Busy : ChatError("BUSY")
    class Network : ChatError("NETWORK")
    class ImageTooLarge : ChatError("IMAGE_TOO_LARGE")
    class ImageUnreadable : ChatError("IMAGE_UNREADABLE")
    data class Unknown(val rawCode: String?) : ChatError(rawCode ?: "UNKNOWN")

    /**
     * `true` với lỗi tạm thời (mạng, `503 BUSY`): outbox tự gửi lại khi có mạng / sau một nhịp chờ.
     * `false` với lỗi nghiệp vụ cố định: chuyển `FAILED` ngay và chờ người dùng quyết định.
     */
    val isRetryable: Boolean
        get() = this is Network || this is Busy
}

fun chatErrorOf(code: String?, httpStatus: Int? = null): ChatError = when (code) {
    "INVALID_ARGUMENT" -> ChatError.InvalidArgument()
    "PHOTO_MISSING" -> ChatError.PhotoMissing()
    "UNAUTHENTICATED" -> ChatError.Unauthenticated()
    "ACCOUNT_LOCKED" -> ChatError.AccountLocked()
    "NOT_PARTICIPANT" -> ChatError.NotParticipant()
    "BLOCKED" -> ChatError.Blocked()
    "FORBIDDEN" -> ChatError.Forbidden()
    "OWN_VENUE" -> ChatError.OwnVenue()
    "OWN_MESSAGE" -> ChatError.OwnMessage()
    "CHAT_NOT_FOUND" -> ChatError.ChatNotFound()
    "MESSAGE_NOT_FOUND" -> ChatError.MessageNotFound()
    "CHAT_READ_ONLY" -> ChatError.ChatReadOnly()
    "VENUE_INACTIVE" -> ChatError.VenueInactive()
    "OWNER_NOT_APPROVED" -> ChatError.OwnerNotApproved()
    "RECALL_WINDOW_PASSED" -> ChatError.RecallWindowPassed()
    "ALREADY_REPORTED" -> ChatError.AlreadyReported()
    "GROUP_LOCKED" -> ChatError.GroupLocked()
    "BUSY" -> ChatError.Busy()
    "NETWORK" -> ChatError.Network()
    "IMAGE_TOO_LARGE" -> ChatError.ImageTooLarge()
    "IMAGE_UNREADABLE" -> ChatError.ImageUnreadable()
    null -> when (httpStatus) {
        401 -> ChatError.Unauthenticated()
        423 -> ChatError.GroupLocked()
        503 -> ChatError.Busy()
        else -> ChatError.Unknown(null)
    }
    else -> ChatError.Unknown(code)
}

fun Throwable.toChatError(): ChatError = when (this) {
    is ChatError -> this
    AppError.Network, AppError.Timeout -> ChatError.Network()
    AppError.Locked -> ChatError.AccountLocked()
    AppError.NotSignedIn, AppError.SessionRevoked, AppError.AccountDeleted -> ChatError.Unauthenticated()
    AppError.Conflict -> ChatError.Busy()
    AppError.RateLimited -> ChatError.Unknown("RATE_LIMITED")
    is AppError.Unknown -> when (val c = cause) {
        is FunctionHttpException -> c.toChatError()
        is FirebaseFirestoreException -> c.toFirestoreChatError()
        is FirebaseNetworkException, is IOException, is HttpRequestException -> ChatError.Network()
        is ImageTooLargeException -> ChatError.ImageTooLarge()
        is ImageUnreadableException -> ChatError.ImageUnreadable()
        else -> ChatError.Unknown(null)
    }
    is FunctionHttpException -> toChatError()
    is FirebaseFirestoreException -> toFirestoreChatError()
    is FirebaseNetworkException, is IOException, is HttpRequestException -> ChatError.Network()
    is ImageTooLargeException -> ChatError.ImageTooLarge()
    is ImageUnreadableException -> ChatError.ImageUnreadable()
    else -> ChatError.Unknown(null)
}

private fun FunctionHttpException.toChatError(): ChatError =
    chatErrorOf(parseFunctionErrorCode(body), status)

private fun FirebaseFirestoreException.toFirestoreChatError(): ChatError = when (code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED -> ChatError.NotParticipant()
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ChatError.Network()
    FirebaseFirestoreException.Code.UNAUTHENTICATED -> ChatError.Unauthenticated()
    FirebaseFirestoreException.Code.NOT_FOUND -> ChatError.ChatNotFound()
    else -> ChatError.Unknown(code.name)
}

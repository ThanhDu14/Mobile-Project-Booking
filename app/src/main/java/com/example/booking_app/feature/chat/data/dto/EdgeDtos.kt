@file:OptIn(ExperimentalSerializationApi::class)

package com.example.booking_app.feature.chat.data.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Request/Response của 3 Edge Function `chat-send`, `chat-action`, `chat-media-url`
 * khớp đúng `handler.ts` trên server và `contracts/edge-functions-chat.md`.
 */
val ChatFunctionJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

// ---------- chat-send ----------

@Serializable
data class ChatSendRequest(
    val chatId: String,
    val messageId: String,
    val text: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imagePath: String? = null,
)

@Serializable
data class ChatMessageResultDto(
    val id: String,
    val senderId: String? = null,
    val senderSnapshot: ChatUserSnapshotDto? = null,
    val senderDeleted: Boolean = false,
    val text: String? = null,
    val imagePath: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val recalledAt: String? = null,
)

@Serializable
data class ChatSendResponse(
    val message: ChatMessageResultDto? = null,
)

// ---------- chat-action ----------

@Serializable
data class ChatActionRequest(
    val action: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val venueId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val chatId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val messageId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val reason: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val detail: String? = null,
) {
    companion object {
        fun openDirect(venueId: String) = ChatActionRequest(action = "openDirect", venueId = venueId)

        fun recall(chatId: String, messageId: String) =
            ChatActionRequest(action = "recall", chatId = chatId, messageId = messageId)

        fun report(chatId: String, messageId: String, reason: String, detail: String?) =
            ChatActionRequest(
                action = "report",
                chatId = chatId,
                messageId = messageId,
                reason = reason,
                detail = detail,
            )
    }
}

@Serializable
data class ChatActionResponse(
    /** `openDirect`. */
    val chatId: String? = null,
    val created: Boolean = false,
    /** `recall`. */
    val recalled: Boolean? = null,
    /** `report`. */
    val reportId: String? = null,
)

// ---------- chat-media-url ----------

@Serializable
data class ChatMediaRequest(
    val action: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val chatId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val messageId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val contentType: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val size: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val paths: List<String>? = null,
) {
    companion object {
        fun upload(chatId: String, messageId: String, contentType: String, size: Long) =
            ChatMediaRequest(
                action = "upload",
                chatId = chatId,
                messageId = messageId,
                contentType = contentType,
                size = size,
            )

        fun download(paths: List<String>) = ChatMediaRequest(action = "download", paths = paths)

        fun discard(chatId: String, messageId: String) =
            ChatMediaRequest(action = "discard", chatId = chatId, messageId = messageId)
    }
}

@Serializable
data class ChatMediaResponse(
    /** `upload`. */
    val path: String? = null,
    val signedUrl: String? = null,
    val token: String? = null,
    /** `download`. */
    val urls: Map<String, String> = emptyMap(),
    val expiresAt: String? = null,
    /** `discard`. */
    val deleted: Int? = null,
)

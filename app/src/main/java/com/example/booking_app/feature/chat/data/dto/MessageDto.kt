package com.example.booking_app.feature.chat.data.dto

import java.time.Instant

/** Document `chats/{chatId}/messages/{messageId}` (data-model mục 3). */
data class MessageDto(
    val id: String,
    val senderId: String? = null,
    val senderSnapshot: ChatUserSnapshotDto? = null,
    val senderDeleted: Boolean = false,
    val text: String? = null,
    val imagePath: String? = null,
    val createdAt: Instant? = null,
    val status: String? = null,
    val recalledAt: Instant? = null,
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>) = MessageDto(
            id = id,
            senderId = data.string("senderId"),
            senderSnapshot = ChatUserSnapshotDto.fromAny(data["senderSnapshot"]),
            senderDeleted = data["senderDeleted"] as? Boolean ?: false,
            text = data.string("text"),
            imagePath = data.string("imagePath")?.takeIf { it.isNotBlank() },
            createdAt = data["createdAt"].asInstant(),
            status = data.string("status"),
            recalledAt = data["recalledAt"].asInstant(),
        )
    }
}

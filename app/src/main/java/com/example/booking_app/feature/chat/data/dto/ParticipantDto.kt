package com.example.booking_app.feature.chat.data.dto

import java.time.Instant

/** Document `chats/{chatId}/participants/{uid}` (data-model mục 2). */
data class ParticipantDto(
    val userId: String,
    val chatId: String? = null,
    val userSnapshot: ChatUserSnapshotDto? = null,
    val joinedAt: Instant? = null,
    val lastReadAt: Instant? = null,
    val muted: Boolean = false,
    val blockedOther: Boolean = false,
    val viewingUntil: Instant? = null,
) {
    companion object {
        fun fromMap(userId: String, data: Map<String, Any?>) = ParticipantDto(
            userId = data.string("userId") ?: userId,
            chatId = data.string("chatId"),
            userSnapshot = ChatUserSnapshotDto.fromAny(data["userSnapshot"]),
            joinedAt = data["joinedAt"].asInstant(),
            lastReadAt = data["lastReadAt"].asInstant(),
            muted = data["muted"] as? Boolean ?: false,
            blockedOther = data["blockedOther"] as? Boolean ?: false,
            viewingUntil = data["viewingUntil"].asInstant(),
        )
    }
}

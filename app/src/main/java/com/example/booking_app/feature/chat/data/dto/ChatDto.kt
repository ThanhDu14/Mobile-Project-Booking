package com.example.booking_app.feature.chat.data.dto

import kotlinx.serialization.Serializable
import java.time.Instant

/** Snapshot tên/ảnh người dùng (`userSnapshot`, `senderSnapshot`, `memberSnapshots[uid]`). */
@Serializable
data class ChatUserSnapshotDto(
    val displayName: String? = null,
    val avatarPath: String? = null,
) {
    companion object {
        fun fromAny(raw: Any?): ChatUserSnapshotDto? {
            val map = raw as? Map<*, *> ?: return null
            return ChatUserSnapshotDto(
                displayName = map["displayName"] as? String,
                avatarPath = (map["avatarPath"] as? String)?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/** Snapshot cơ sở được hỏi gần nhất (`venueSnapshot`). */
data class VenueSnapshotDto(
    val name: String? = null,
    val photoPath: String? = null,
) {
    companion object {
        fun fromAny(raw: Any?): VenueSnapshotDto? {
            val map = raw as? Map<*, *> ?: return null
            return VenueSnapshotDto(
                name = map["name"] as? String,
                photoPath = (map["photoPath"] as? String)?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/** `chats/{chatId}.lastMessage`. */
data class LastMessageDto(
    val messageId: String? = null,
    val senderId: String? = null,
    val type: String? = null,
    val preview: String? = null,
    val createdAt: Instant? = null,
) {
    companion object {
        fun fromAny(raw: Any?): LastMessageDto? {
            val map = raw as? Map<*, *> ?: return null
            return LastMessageDto(
                messageId = map["messageId"] as? String,
                senderId = map["senderId"] as? String,
                type = map["type"] as? String,
                preview = map["preview"] as? String,
                createdAt = map["createdAt"].asInstant(),
            )
        }
    }
}

/** Document `chats/{chatId}` (data-model mục 1). */
data class ChatDto(
    val id: String,
    val type: String? = null,
    val memberIds: List<String> = emptyList(),
    val groupId: String? = null,
    val ownerId: String? = null,
    val playerId: String? = null,
    val venueId: String? = null,
    val venueSnapshot: VenueSnapshotDto? = null,
    val memberSnapshots: Map<String, ChatUserSnapshotDto?> = emptyMap(),
    val lastMessage: LastMessageDto? = null,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val status: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): ChatDto {
            val members = (data["memberIds"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val rawSnapshots = data["memberSnapshots"] as? Map<*, *>
            val snapshots = buildMap<String, ChatUserSnapshotDto?> {
                rawSnapshots?.forEach { (k, v) ->
                    val key = k as? String ?: return@forEach
                    put(key, ChatUserSnapshotDto.fromAny(v))
                }
            }
            val rawUnread = data["unreadCounts"] as? Map<*, *>
            val unread = buildMap<String, Int> {
                rawUnread?.forEach { (k, v) ->
                    val key = k as? String ?: return@forEach
                    put(key, (v.asLong() ?: 0L).coerceAtLeast(0L).toInt())
                }
            }
            return ChatDto(
                id = id,
                type = data.string("type"),
                memberIds = members,
                groupId = data.string("groupId"),
                ownerId = data.string("ownerId"),
                playerId = data.string("playerId"),
                venueId = data.string("venueId"),
                venueSnapshot = VenueSnapshotDto.fromAny(data["venueSnapshot"]),
                memberSnapshots = snapshots,
                lastMessage = LastMessageDto.fromAny(data["lastMessage"]),
                unreadCounts = unread,
                status = data.string("status"),
                createdAt = data["createdAt"].asInstant(),
                updatedAt = data["updatedAt"].asInstant(),
            )
        }
    }
}

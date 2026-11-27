package com.example.booking_app.feature.chat.domain.model

import java.time.Instant

/** Trạng thái của chính người dùng trong một cuộc trò chuyện (`chats/{chatId}/participants/{uid}`). */
data class Participant(
    val muted: Boolean = false,
    val blockedOther: Boolean = false,
    val lastReadAt: Instant? = null,
)

/** Tóm tắt cơ sở được hỏi gần nhất trong chat riêng. */
data class VenueSummary(
    val id: String,
    val name: String,
    val photoPath: String? = null,
)

/** Tóm tắt bên kia trong chat riêng. */
data class UserSummary(
    val userId: String,
    val displayName: String? = null,
    val avatarPath: String? = null,
    val isDeleted: Boolean = false,
)

/** Thông tin tiêu đề của chat nhóm đọc từ `groups/{groupId}`. */
data class GroupHeaderInfo(
    val groupId: String,
    val name: String,
    val avatarPath: String? = null,
    val memberCount: Int = 0,
    val isLocked: Boolean = false,
)

/** Chi tiết cuộc trò chuyện cho màn hình `ChatFragment` (data-model mục 7). */
data class ChatDetail(
    val id: String,
    val type: ChatType,
    val memberIds: List<String>,
    val groupId: String? = null,
    val ownerId: String? = null,
    val playerId: String? = null,
    val venue: VenueSummary? = null,
    val otherParty: UserSummary? = null,
    val status: ChatStatus = ChatStatus.ACTIVE,
    val myParticipant: Participant = Participant(),
)

/** Kết quả mở chat riêng từ `chat-action openDirect`. */
data class DirectChatResult(
    val chatId: String,
    val created: Boolean,
)

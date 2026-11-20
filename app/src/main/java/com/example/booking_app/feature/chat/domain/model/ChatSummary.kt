package com.example.booking_app.feature.chat.domain.model

import java.time.Instant

/** Một dòng trong danh sách cuộc trò chuyện (data-model mục 7). */
data class ChatSummary(
    val id: String,
    val type: ChatType,
    val title: String,
    val avatarPath: String? = null,
    val avatarBucket: String? = null,
    val groupId: String? = null,
    val ownerId: String? = null,
    val playerId: String? = null,
    val venueId: String? = null,
    val venueName: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageType: MessageType? = null,
    val lastMessageSenderId: String? = null,
    val updatedAt: Instant? = null,
    val unreadCount: Int = 0,
    val isReadOnly: Boolean = false,
    val muted: Boolean = false,
    val blockedOther: Boolean = false,
    val status: ChatStatus = ChatStatus.ACTIVE,
)

package com.example.booking_app.feature.chat.data.dto

import com.example.booking_app.feature.chat.domain.model.ChatDetail
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.ChatSignedUrls
import com.example.booking_app.feature.chat.domain.model.ChatStatus
import com.example.booking_app.feature.chat.domain.model.ChatSummary
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.domain.model.Delivery
import com.example.booking_app.feature.chat.domain.model.DirectChatResult
import com.example.booking_app.feature.chat.domain.model.GroupHeaderInfo
import com.example.booking_app.feature.chat.domain.model.MessageStatus
import com.example.booking_app.feature.chat.domain.model.MessageType
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.example.booking_app.feature.chat.domain.model.Participant
import com.example.booking_app.feature.chat.domain.model.SignedChatUpload
import com.example.booking_app.feature.chat.domain.model.UserSummary
import com.example.booking_app.feature.chat.domain.model.VenueSummary
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date
import java.util.Locale

internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String

internal fun Any?.asInstant(): Instant? = when (this) {
    is Timestamp -> toInstant()
    is Date -> toInstant()
    is Instant -> this
    is String -> runCatching { Instant.parse(this) }.getOrNull()
    else -> null
}

internal fun Any?.asLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}

private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { raw -> enumValues<E>().firstOrNull { it.name == raw.trim().uppercase(Locale.ROOT) } }

fun ParticipantDto?.toDomain(): Participant = Participant(
    muted = this?.muted ?: false,
    blockedOther = this?.blockedOther ?: false,
    lastReadAt = this?.lastReadAt,
)

fun ChatDto.toDetail(currentUid: String, myParticipant: Participant = Participant()): ChatDetail {
    val chatType = type.toEnumOrNull<ChatType>() ?: if (groupId != null) ChatType.GROUP else ChatType.DIRECT
    val chatStatus = status.toEnumOrNull<ChatStatus>() ?: ChatStatus.ACTIVE
    val venue = venueId?.let { vid ->
        VenueSummary(
            id = vid,
            name = venueSnapshot?.name.orEmpty(),
            photoPath = venueSnapshot?.photoPath,
        )
    }
    val otherParty = if (chatType == ChatType.DIRECT) {
        val otherUid = memberIds.firstOrNull { it != currentUid }
            ?: if (currentUid == ownerId) playerId else ownerId
        otherUid?.let { uid ->
            val snapshot = memberSnapshots[uid]
            val deleted = memberSnapshots.containsKey(uid) && snapshot == null
            UserSummary(
                userId = uid,
                displayName = snapshot?.displayName,
                avatarPath = snapshot?.avatarPath,
                isDeleted = deleted || chatStatus == ChatStatus.READ_ONLY && snapshot == null,
            )
        }
    } else {
        null
    }
    return ChatDetail(
        id = id,
        type = chatType,
        memberIds = memberIds,
        groupId = groupId,
        ownerId = ownerId,
        playerId = playerId,
        venue = venue,
        otherParty = otherParty,
        status = chatStatus,
        myParticipant = myParticipant,
    )
}

/** Chuyển [ChatDto] sang [ChatSummary]; thông tin tên/ảnh nhóm truyền qua [groupInfo] (US2/US3). */
fun ChatDto.toSummary(
    currentUid: String,
    groupInfo: GroupHeaderInfo? = null,
    myParticipant: Participant? = null,
): ChatSummary {
    val chatType = type.toEnumOrNull<ChatType>() ?: if (groupId != null) ChatType.GROUP else ChatType.DIRECT
    val chatStatus = status.toEnumOrNull<ChatStatus>() ?: ChatStatus.ACTIVE
    val otherUid = if (chatType == ChatType.DIRECT) {
        memberIds.firstOrNull { it != currentUid }
            ?: if (currentUid == ownerId) playerId else ownerId
    } else {
        null
    }
    val otherSnapshot = otherUid?.let { memberSnapshots[it] }
    val title = when (chatType) {
        ChatType.GROUP -> groupInfo?.name.orEmpty()
        ChatType.DIRECT -> otherSnapshot?.displayName.orEmpty()
    }
    val avatarPath = when (chatType) {
        ChatType.GROUP -> groupInfo?.avatarPath
        ChatType.DIRECT -> otherSnapshot?.avatarPath
    }
    val avatarBucket = when (chatType) {
        ChatType.GROUP -> "group-avatars"
        ChatType.DIRECT -> "avatars"
    }
    return ChatSummary(
        id = id,
        type = chatType,
        title = title,
        avatarPath = avatarPath,
        avatarBucket = avatarBucket,
        groupId = groupId,
        ownerId = ownerId,
        playerId = playerId,
        venueId = venueId,
        venueName = venueSnapshot?.name,
        lastMessagePreview = lastMessage?.preview,
        lastMessageType = lastMessage?.type.toEnumOrNull<MessageType>(),
        lastMessageSenderId = lastMessage?.senderId,
        updatedAt = updatedAt ?: lastMessage?.createdAt ?: createdAt,
        unreadCount = (unreadCounts[currentUid] ?: 0).coerceAtLeast(0),
        isReadOnly = chatStatus == ChatStatus.READ_ONLY,
        muted = myParticipant?.muted ?: false,
        blockedOther = myParticipant?.blockedOther ?: false,
        status = chatStatus,
    )
}

fun MessageDto.toDomain(currentUid: String): ChatMessage {
    val msgStatus = status.toEnumOrNull<MessageStatus>() ?: MessageStatus.NORMAL
    val sender = senderId.orEmpty()
    return ChatMessage(
        id = id,
        senderId = sender,
        senderName = if (senderDeleted) null else senderSnapshot?.displayName,
        senderAvatarPath = if (senderDeleted) null else senderSnapshot?.avatarPath,
        senderDeleted = senderDeleted,
        text = if (msgStatus == MessageStatus.NORMAL) text.orEmpty() else "",
        imagePath = if (msgStatus == MessageStatus.NORMAL) imagePath else null,
        createdAt = createdAt,
        status = msgStatus,
        delivery = Delivery.SENT,
        isMine = sender.isNotEmpty() && sender == currentUid,
    )
}

fun OutboxEntry.toChatMessage(currentUid: String): ChatMessage = ChatMessage(
    id = messageId,
    senderId = currentUid,
    text = text,
    imagePath = imagePath,
    localImageUri = localImageUri,
    createdAt = Instant.ofEpochMilli(createdAtLocal),
    status = MessageStatus.NORMAL,
    delivery = status,
    isMine = true,
    failureCode = errorCode,
)

fun ChatActionResponse.toOpenDirectResult(): DirectChatResult? {
    val id = chatId?.takeIf { it.isNotBlank() } ?: return null
    return DirectChatResult(chatId = id, created = created)
}

fun ChatMediaResponse.toSignedUpload(): SignedChatUpload? {
    val p = path?.takeIf { it.isNotBlank() } ?: return null
    val url = signedUrl?.takeIf { it.isNotBlank() } ?: return null
    return SignedChatUpload(path = p, signedUrl = url, token = token.orEmpty())
}

fun ChatMediaResponse.toSignedUrls(): ChatSignedUrls =
    ChatSignedUrls(urls = urls, expiresAt = expiresAt.asInstant())

fun groupHeaderFromMap(groupId: String, data: Map<String, Any?>): GroupHeaderInfo = GroupHeaderInfo(
    groupId = groupId,
    name = data.string("name").orEmpty(),
    avatarPath = data.string("avatarPath")?.takeIf { it.isNotBlank() },
    memberCount = (data["memberCount"].asLong() ?: 0L).coerceAtLeast(0L).toInt(),
    isLocked = data.string("status")?.uppercase(Locale.ROOT) == "LOCKED",
)

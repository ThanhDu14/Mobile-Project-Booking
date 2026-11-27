package com.example.booking_app.feature.chat.domain.model

import java.time.Duration
import java.time.Instant

/** Quy tắc kiểm tra tin nhắn chữ và cửa sổ thu hồi (data-model mục 3, FR-006, FR-010). */
object ChatMessageRules {
    const val MAX_TEXT_LENGTH = 1000
    val RECALL_WINDOW: Duration = Duration.ofMinutes(10)

    /** Đếm theo Unicode code point để khớp `Array.from(s).length` ở server `chat-send`. */
    fun codePointLength(text: String): Int = text.codePointCount(0, text.length)

    /** Tin chữ hợp lệ: sau trim có 1–1000 ký tự, không chỉ khoảng trắng. */
    fun isValidText(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        return codePointLength(trimmed) <= MAX_TEXT_LENGTH
    }

    /** Chú thích cho tin ảnh: 0–1000 ký tự sau trim. */
    fun isValidCaption(raw: String): Boolean = codePointLength(raw.trim()) <= MAX_TEXT_LENGTH
}

/** Một tin nhắn trong cuộc trò chuyện (gộp tin server và tin outbox, data-model mục 7). */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String? = null,
    val senderAvatarPath: String? = null,
    val senderDeleted: Boolean = false,
    val text: String = "",
    val imagePath: String? = null,
    val localImageUri: String? = null,
    val createdAt: Instant? = null,
    val status: MessageStatus = MessageStatus.NORMAL,
    val delivery: Delivery = Delivery.SENT,
    val isMine: Boolean = false,
    val failureCode: String? = null,
) {
    /**
     * Thu hồi được khi là tin của mình, đã lên server, ở trạng thái `NORMAL` và `now < createdAt + 10 phút`
     * theo giờ server (khớp `chat-action recall`).
     */
    fun canRecall(now: Instant): Boolean {
        if (!isMine || delivery != Delivery.SENT || status != MessageStatus.NORMAL) return false
        val sentAt = createdAt ?: return false
        return now.isBefore(sentAt.plus(ChatMessageRules.RECALL_WINDOW))
    }

    /** Có thể báo cáo khi là tin của người khác, đã lên server và ở trạng thái `NORMAL`. */
    val canReport: Boolean
        get() = !isMine && delivery == Delivery.SENT && status == MessageStatus.NORMAL
}

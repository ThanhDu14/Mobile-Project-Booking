package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatMessageRules
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import java.util.UUID
import javax.inject.Inject

/**
 * Sinh `messageId` UUID tất định, kiểm tra quy tắc nội dung rồi đưa vào `ChatOutbox` (T028, R1).
 */
class SendMessage @Inject constructor(
    private val repository: ChatRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        chatId: String,
        rawText: String,
        localImageUri: String? = null,
        messageId: String = UUID.randomUUID().toString(),
    ): Result<OutboxEntry> {
        val trimmed = rawText.trim()
        val valid = if (localImageUri != null) {
            ChatMessageRules.isValidCaption(trimmed)
        } else {
            ChatMessageRules.isValidText(trimmed)
        }
        if (!valid) return Result.failure(ChatError.InvalidArgument())

        val entry = OutboxEntry(
            messageId = messageId,
            chatId = chatId,
            text = trimmed,
            localImageUri = localImageUri,
            createdAtLocal = clock.now(),
        )
        return repository.send(entry).map { entry }
    }
}

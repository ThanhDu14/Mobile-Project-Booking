package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import javax.inject.Inject

/** Thu hồi tin nhắn của chính mình trong vòng 10 phút qua `chat-action recall` (FR-010, T051). */
class RecallMessage @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, messageId: String): Result<Unit> =
        repository.recall(chatId, messageId)
}

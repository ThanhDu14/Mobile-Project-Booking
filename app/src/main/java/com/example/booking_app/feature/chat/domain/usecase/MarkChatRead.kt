package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import javax.inject.Inject

/** Đánh dấu đã đọc cuộc trò chuyện: batch `lastReadAt = serverTimestamp()` + `unreadCounts.{mình} = 0` (T028). */
class MarkChatRead @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String): Result<Unit> =
        repository.markRead(chatId)
}

package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import javax.inject.Inject

/** Gửi lại tin nhắn lỗi dùng lại đúng `messageId` (T028). */
class RetryMessage @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, messageId: String): Result<Unit> =
        repository.retry(chatId, messageId)
}

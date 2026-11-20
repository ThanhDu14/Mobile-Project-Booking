package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.DiscardResult
import javax.inject.Inject

/** Xóa tin nhắn lỗi khỏi outbox (kiểm tra server trước xem tin đã commit chưa, T028). */
class DiscardMessage @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, messageId: String): Result<DiscardResult> =
        repository.discard(chatId, messageId)
}

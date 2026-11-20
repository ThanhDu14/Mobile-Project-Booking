package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import java.time.Instant
import javax.inject.Inject

/** Tải thêm 30 tin nhắn cũ hơn [before] khi người dùng cuộn lên (T028, FR-009). */
class LoadOlderMessages @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, before: Instant): Result<List<ChatMessage>> =
        repository.loadOlder(chatId, before)
}

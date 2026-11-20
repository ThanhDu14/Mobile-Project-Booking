package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Lắng nghe 30 tin nhắn mới nhất của cuộc trò chuyện theo thời gian thực (T028). */
class ObserveMessages @Inject constructor(
    private val repository: ChatRepository,
) {
    operator fun invoke(chatId: String): Flow<List<ChatMessage>> =
        repository.observeMessages(chatId)
}

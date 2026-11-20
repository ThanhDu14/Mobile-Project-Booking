package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import javax.inject.Inject

/** Tắt/bật thông báo cho từng cuộc trò chuyện (FR-013, T040). */
class SetMuted @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, muted: Boolean): Result<Unit> =
        repository.setMuted(chatId, muted)
}

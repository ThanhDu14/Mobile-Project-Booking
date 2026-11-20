package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import javax.inject.Inject

/** Chặn hoặc bỏ chặn bên kia trong chat riêng (`participants/{mình}.blockedOther`, FR-016, T051). */
class SetBlocked @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, blocked: Boolean): Result<Unit> =
        repository.setBlocked(chatId, blocked)
}

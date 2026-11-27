package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ReportReason
import javax.inject.Inject

/** Báo cáo tin nhắn vi phạm của người khác qua `chat-action report` (FR-014, T051). */
class ReportMessage @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(
        chatId: String,
        messageId: String,
        reason: ReportReason,
        detail: String?,
    ): Result<String> = repository.report(chatId, messageId, reason, detail)
}

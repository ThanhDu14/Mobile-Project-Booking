package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.DirectChatResult
import javax.inject.Inject

/** Mở hoặc tạo cuộc trò chuyện riêng giữa người chơi và chủ sân từ `venueId` (T028). */
class OpenDirectChat @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(venueId: String): Result<DirectChatResult> =
        repository.openDirect(venueId)
}

package com.example.booking_app.feature.chat.domain

import com.example.booking_app.feature.chat.domain.model.ChatSignedUrls
import com.example.booking_app.feature.chat.domain.model.UploadState
import kotlinx.coroutines.flow.Flow

/**
 * Kho ảnh riêng tư của chat (bucket `chat-media`, T017, research R8).
 */
interface ChatMediaRepository {

    /** Nén bằng `ImageCompressor`, xin signed upload URL từ `chat-media-url`, tải lên và phát trạng thái. */
    fun upload(chatId: String, messageId: String, uri: String): Flow<UploadState>

    /** Xin signed download URL (tối đa 30 path mỗi gọi). */
    suspend fun signedUrls(paths: List<String>): Result<ChatSignedUrls>

    /** Xóa ảnh đã tải lên nhưng chưa tạo được tin nhắn (`chat-media-url discard`). */
    suspend fun discard(chatId: String, messageId: String): Result<Unit>
}

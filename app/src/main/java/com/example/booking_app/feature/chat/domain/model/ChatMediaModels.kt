package com.example.booking_app.feature.chat.domain.model

import com.example.booking_app.feature.chat.domain.ChatError
import java.time.Instant

/** Trạng thái tải ảnh chat lên Supabase Storage (T017). */
sealed interface UploadState {
    data object Uploading : UploadState
    data class Uploaded(val path: String) : UploadState
    data class Failed(val error: ChatError) : UploadState
}

/** Kết quả xin signed URL đọc ảnh chat (`chat-media-url download`). */
data class ChatSignedUrls(
    val urls: Map<String, String>,
    val expiresAt: Instant?,
)

/** Kết quả xin signed URL tải ảnh lên (`chat-media-url upload`). */
data class SignedChatUpload(
    val path: String,
    val signedUrl: String,
    val token: String,
)

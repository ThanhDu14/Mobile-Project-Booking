package com.example.booking_app.feature.account.domain

/** Đọc ảnh đã cắt, kiểm tra định dạng, thu nhỏ và nén JPEG (research R4). Ném `AccountError`. */
interface AvatarImageProcessor {
    suspend fun process(uri: String): ByteArray
}

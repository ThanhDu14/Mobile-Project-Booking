package com.example.booking_app.feature.chat.domain.model

import kotlinx.serialization.Serializable

/** Tin nhắn trong hàng chờ gửi cục bộ `ChatOutbox` (data-model mục 7, research R1). */
@Serializable
data class OutboxEntry(
    val messageId: String,
    val chatId: String,
    val text: String = "",
    val localImageUri: String? = null,
    val imagePath: String? = null,
    val createdAtLocal: Long,
    val attempts: Int = 0,
    val status: Delivery = Delivery.PENDING,
    /** `true` khi lỗi do mạng/`BUSY` (tự gửi lại khi có mạng); `false` khi lỗi không thử lại được. */
    val retryable: Boolean = true,
    val errorCode: String? = null,
)

/** Kết quả khi người dùng bấm "Xóa" trên một tin trong outbox (research R1). */
enum class DiscardResult {
    /** Đã xóa khỏi outbox (và dọn ảnh rác nếu có). */
    DISCARDED,
    /** Tin thực tế đã lên server trước đó → đánh dấu đã gửi thay vì xóa. */
    ALREADY_SENT,
}

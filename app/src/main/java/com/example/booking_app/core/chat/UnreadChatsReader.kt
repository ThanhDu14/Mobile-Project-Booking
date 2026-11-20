package com.example.booking_app.core.chat

import kotlinx.coroutines.flow.Flow

/**
 * Interface đọc tổng số cuộc trò chuyện còn tin chưa đọc (spec 091, contracts/ui-screens.md mục 3).
 * Đặt trong `core/chat` để `MainActivity` gắn huy hiệu mà không import `feature/chat`.
 */
interface UnreadChatsReader {
    /** Số cuộc trò chuyện có `unreadCounts[uid] > 0`; về 0 khi chưa đăng nhập. */
    fun observeUnreadChatCount(): Flow<Int>
}

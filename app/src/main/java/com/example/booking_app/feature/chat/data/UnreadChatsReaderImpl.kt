package com.example.booking_app.feature.chat.data

import com.example.booking_app.core.chat.UnreadChatsReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Khung ban đầu cho T018; cài đặt đầy đủ ở T039 (US3). */
@Singleton
class UnreadChatsReaderImpl @Inject constructor() : UnreadChatsReader {
    override fun observeUnreadChatCount(): Flow<Int> = flowOf(0)
}

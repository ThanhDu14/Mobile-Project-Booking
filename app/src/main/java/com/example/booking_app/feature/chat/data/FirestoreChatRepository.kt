package com.example.booking_app.feature.chat.data

import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatDetail
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.ChatMode
import com.example.booking_app.feature.chat.domain.model.ChatSummary
import com.example.booking_app.feature.chat.domain.model.DirectChatResult
import com.example.booking_app.feature.chat.domain.model.DiscardResult
import com.example.booking_app.feature.chat.domain.model.GroupHeaderInfo
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.example.booking_app.feature.chat.domain.model.ReportReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Khung ban đầu cho T018; cài đặt đầy đủ ở T027 (US1), T035/T038 (US2/US3), T051 (US5). */
@Singleton
class FirestoreChatRepository @Inject constructor(
    private val outbox: ChatOutbox,
) : ChatRepository {
    override fun observeChats(mode: ChatMode): Flow<List<ChatSummary>> = flowOf(emptyList())
    override fun observeChat(chatId: String): Flow<ChatDetail?> = flowOf(null)
    override fun observeGroupHeader(groupId: String): Flow<GroupHeaderInfo?> = flowOf(null)
    override suspend fun openDirect(venueId: String): Result<DirectChatResult> =
        Result.failure(ChatError.Unknown("NOT_IMPLEMENTED"))
    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> = flowOf(emptyList())
    override suspend fun loadOlder(chatId: String, before: Instant): Result<List<ChatMessage>> =
        Result.success(emptyList())
    override fun observeOutbox(chatId: String): Flow<List<OutboxEntry>> = outbox.observe(chatId)
    override suspend fun send(entry: OutboxEntry): Result<Unit> {
        outbox.enqueue(entry)
        return Result.success(Unit)
    }
    override suspend fun retry(chatId: String, messageId: String): Result<Unit> {
        outbox.retry(chatId, messageId)
        return Result.success(Unit)
    }
    override suspend fun discard(chatId: String, messageId: String): Result<DiscardResult> =
        Result.success(outbox.discard(chatId, messageId))
    override suspend fun markRead(chatId: String): Result<Unit> = Result.success(Unit)
    override suspend fun setViewing(chatId: String, until: Instant?): Result<Unit> = Result.success(Unit)
    override suspend fun setMuted(chatId: String, muted: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun setBlocked(chatId: String, blocked: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun recall(chatId: String, messageId: String): Result<Unit> =
        Result.failure(ChatError.Unknown("NOT_IMPLEMENTED"))
    override suspend fun report(
        chatId: String,
        messageId: String,
        reason: ReportReason,
        detail: String?,
    ): Result<String> = Result.failure(ChatError.Unknown("NOT_IMPLEMENTED"))
}

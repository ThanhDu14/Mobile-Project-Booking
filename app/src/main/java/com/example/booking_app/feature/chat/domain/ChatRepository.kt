package com.example.booking_app.feature.chat.domain

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
import java.time.Instant

/**
 * Kho dữ liệu cuộc trò chuyện và tin nhắn của spec 091 (T017).
 * Đọc realtime qua Firestore listener (`callbackFlow`); gửi tin qua `ChatOutbox` → Edge Function `chat-send`;
 * thao tác nghiệp vụ qua `chat-action`; cập nhật trạng thái đọc/tắt thông báo/chặn trực tiếp trên Rules cho phép.
 */
interface ChatRepository {

    fun observeChats(mode: ChatMode): Flow<List<ChatSummary>>

    fun observeChat(chatId: String): Flow<ChatDetail?>

    /** Đọc thông tin tên/ảnh/trạng thái khóa của nhóm từ `groups/{groupId}` cho đầu màn hình chat nhóm. */
    fun observeGroupHeader(groupId: String): Flow<GroupHeaderInfo?>

    suspend fun openDirect(venueId: String): Result<DirectChatResult>

    /** 30 tin nhắn mới nhất theo thời gian thực (`orderBy createdAt desc limit 30`), trả theo thứ tự cũ → mới. */
    fun observeMessages(chatId: String): Flow<List<ChatMessage>>

    /** Tải thêm tối đa 30 tin cũ hơn [before] (một lần đọc, không listener), trả theo thứ tự cũ → mới. */
    suspend fun loadOlder(chatId: String, before: Instant): Result<List<ChatMessage>>

    /** Hàng chờ gửi cục bộ của [chatId]. */
    fun observeOutbox(chatId: String): Flow<List<OutboxEntry>>

    /** Đưa tin vào outbox (hiện `PENDING` ngay) và kích hoạt gửi. */
    suspend fun send(entry: OutboxEntry): Result<Unit>

    /** Gửi lại tin lỗi dùng đúng `messageId`. */
    suspend fun retry(chatId: String, messageId: String): Result<Unit>

    /** Xóa tin lỗi khỏi outbox (nếu server đã có tin thì trả [DiscardResult.ALREADY_SENT]). */
    suspend fun discard(chatId: String, messageId: String): Result<DiscardResult>

    /** Batch ghi `participants/{mình}.lastReadAt = serverTimestamp()` và `chats.unreadCounts.{mình} = 0`. */
    suspend fun markRead(chatId: String): Result<Unit>

    /** Cập nhật `participants/{mình}.viewingUntil` (null khi rời màn hình, R7). */
    suspend fun setViewing(chatId: String, until: Instant?): Result<Unit>

    /** Cập nhật `participants/{mình}.muted` (FR-013). */
    suspend fun setMuted(chatId: String, muted: Boolean): Result<Unit>

    /** Cập nhật `participants/{mình}.blockedOther` cho chat riêng (FR-016). */
    suspend fun setBlocked(chatId: String, blocked: Boolean): Result<Unit>

    /** Thu hồi tin của mình trong vòng 10 phút qua `chat-action recall` (FR-010). */
    suspend fun recall(chatId: String, messageId: String): Result<Unit>

    /** Báo cáo tin nhắn của người khác qua `chat-action report` (FR-014). */
    suspend fun report(chatId: String, messageId: String, reason: ReportReason, detail: String?): Result<String>

    /**
     * Độ lệch ước lượng (ms) giữa giờ server và giờ máy (`serverMs - localMs`), đo từ lần ghi
     * `lastReadAt = serverTimestamp()` gần nhất (research R7).
     */
    fun estimatedServerOffsetMs(): Long = 0L
}

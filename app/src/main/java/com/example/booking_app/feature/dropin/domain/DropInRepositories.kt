package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.CancelQuote
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import kotlinx.coroutines.flow.Flow

/** Đọc buổi vãng lai (T012, research R6). */
interface DropInSessionRepository {
    /** Buổi `OPEN`/`FULL`, bắt đầu từ bây giờ tới 30 ngày tới, đã lọc cơ sở không `ACTIVE`. */
    suspend fun page(filter: DropInFilter, cursor: DropInCursor?): Result<Page<DropInSession>>

    /** Realtime; `null` khi buổi không tồn tại. */
    fun observe(sessionId: String): Flow<DropInSession?>
}

/** Đăng ký và hủy của người đang đăng nhập (T012). Lỗi nghiệp vụ trả `Result.failure(DropInError)`. */
interface DropInRegistrationRepository {
    suspend fun register(req: RegisterRequest): Result<DropInRegistration>

    /** Thêm [additional] người vào đăng ký đang có (FR-008a); gửi lại cùng [requestId] không cộng hai lần. */
    suspend fun addParty(sessionId: String, additional: Int, requestId: String): Result<DropInRegistration>

    /** US4: số tiền được hoàn nếu hủy lúc này (`dryRun`, không ghi gì). */
    suspend fun quoteCancel(sessionId: String, requestId: String): Result<CancelQuote>

    /** US4: hủy lượt của mình; gửi lại cùng [requestId] không trả chỗ hai lần. */
    suspend fun cancel(sessionId: String, requestId: String): Result<DropInRegistration>

    /** Đăng ký của chính mình ở buổi này; `null` khi chưa có hoặc là khách. */
    fun observeMine(sessionId: String): Flow<DropInRegistration?>

    /**
     * Lượt `REGISTERED`/`WAITLISTED` của [uid] có `sessionStartAt >= now`, giờ gần nhất trước, realtime (research R7).
     * Bị Rules từ chối (vừa đăng xuất) → phát danh sách rỗng.
     */
    fun observeUpcoming(uid: String): Flow<List<DropInRegistration>>

    /**
     * Trang "Đã qua": mọi lượt của [uid] theo `sessionStartAt` mới nhất trước, mỗi trang 20 document; đã bỏ những
     * lượt thuộc "Sắp tới". Con trỏ lấy từ document thô cuối trang ([DropInCursor.id] là đường dẫn document).
     */
    suspend fun pagePast(uid: String, cursor: DropInCursor?): Result<Page<DropInRegistration>>
}

/**
 * Đọc nhiều buổi một lần để hiển thị "Lượt vãng lai của tôi" (T045, research R7): mỗi lô tối đa 20 ID.
 * Không ném lỗi: buổi không tồn tại hoặc không đọc được thì không có trong kết quả.
 */
interface DropInSessionLookup {
    suspend fun getByIds(ids: Collection<String>): Map<String, DropInSession>
}

/** Cho nhóm khác (B, spec 060) đọc lượt sắp tới (contracts/ui-screens.md mục 3). */
interface DropInRegistrationReader {
    fun observeUpcoming(uid: String): Flow<List<DropInRegistration>>
}

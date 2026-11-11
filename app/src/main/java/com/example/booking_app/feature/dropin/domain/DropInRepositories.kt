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

    /** US4, tuần 7. */
    suspend fun quoteCancel(sessionId: String, requestId: String): Result<CancelQuote>

    /** US4, tuần 7. */
    suspend fun cancel(sessionId: String, requestId: String): Result<DropInRegistration>

    /** Đăng ký của chính mình ở buổi này; `null` khi chưa có hoặc là khách. */
    fun observeMine(sessionId: String): Flow<DropInRegistration?>

    /** US3, tuần 7. */
    fun observeUpcoming(uid: String): Flow<List<DropInRegistration>>

    /** US3, tuần 7. */
    suspend fun pagePast(uid: String, cursor: DropInCursor?): Result<Page<DropInRegistration>>
}

/** Cho nhóm khác (B, spec 060) đọc lượt sắp tới (contracts/ui-screens.md mục 3). */
interface DropInRegistrationReader {
    fun observeUpcoming(uid: String): Flow<List<DropInRegistration>>
}

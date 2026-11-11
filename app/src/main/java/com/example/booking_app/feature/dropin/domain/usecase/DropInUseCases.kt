package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.DropInWarning
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import kotlinx.coroutines.flow.Flow

/*
 * Chữ ký usecase mà ViewModel dùng (hợp đồng giữa nhóm domain/data và nhóm UI).
 * Agent domain/data cài phần thân (T023, T036) và có thể tách mỗi interface ra file riêng,
 * nhưng KHÔNG đổi chữ ký. ViewModel inject các interface này; test ViewModel mock bằng MockK.
 */

/** T023: trang buổi theo bộ lọc. */
fun interface LoadDropInPage {
    suspend operator fun invoke(filter: DropInFilter, cursor: DropInCursor?): Result<Page<DropInSession>>
}

/** T023: bộ lọc mặc định, điền sẵn khu vực yêu thích từ hồ sơ (AC 1.2). Khách thì trả bộ lọc rỗng. */
fun interface GetDefaultDropInFilter {
    suspend operator fun invoke(): DropInFilter
}

/** T023: buổi realtime. */
fun interface ObserveDropInSession {
    operator fun invoke(sessionId: String): Flow<DropInSession?>
}

/** Đăng ký của mình ở một buổi, realtime (dùng cho chi tiết và vé). */
fun interface ObserveMyDropInRegistration {
    operator fun invoke(sessionId: String): Flow<DropInRegistration?>
}

/** T036: gọi `register-drop-in`. Không tự thử lại khi `Busy`; ViewModel lo việc đó (T032). */
fun interface RegisterDropIn {
    suspend operator fun invoke(req: RegisterRequest): Result<DropInRegistration>
}

/** FR-008a: thêm người vào lượt đang có (vé). Không tự thử lại; ViewModel giữ [requestId] để thử lại an toàn. */
fun interface AddDropInParty {
    suspend operator fun invoke(sessionId: String, additional: Int, requestId: String): Result<DropInRegistration>
}

/** T036: cảnh báo trùng giờ / lệch trình độ (FR-012). Lỗi đọc dữ liệu thì trả danh sách rỗng. */
fun interface CheckDropInWarnings {
    suspend operator fun invoke(session: DropInSession): List<DropInWarning>
}

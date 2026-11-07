package com.example.booking_app.core.network

/**
 * Lỗi dùng chung cho mọi tính năng. Repository ném các lỗi này; ViewModel chỉ cần phân biệt
 * vài trường hợp để hiện thông báo (FR-021).
 */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** Không có mạng. Hiện "Không có kết nối mạng" kèm "Thử lại". */
    data object Network : AppError("Network unavailable")

    /** Quá 15 giây không có phản hồi; xử lý như mất mạng. */
    data object Timeout : AppError("Request timed out")

    /** Firebase hoặc Edge Function báo gửi quá nhiều yêu cầu. */
    data object RateLimited : AppError("Too many requests")

    /**
     * Tranh chấp tài nguyên, vd transaction Firestore bị abort do nhiều client giữ cùng slot.
     * Không phải tài khoản bị khóa, không trigger đăng xuất (chỉ Locked mới trigger signOutLocked trong FirebaseSessionManager).
     */
    data object Conflict : AppError("Resource conflict")

    /** Tài khoản bị khóa (FR-014). `SessionManager` tự đăng xuất khi gặp lỗi này. */
    data object Locked : AppError("Account locked")

    /** Thao tác cần đăng nhập mà đang ở chế độ khách hoặc token hết hiệu lực. */
    data object NotSignedIn : AppError("Not signed in")

    /** [011] Server trả `ACCOUNT_DELETING`: tài khoản đã được xóa. `SessionManager` tự đăng xuất. */
    data object AccountDeleted : AppError("Account deleted")

    /**
     * [011] Firebase thu hồi phiên (`ERROR_USER_TOKEN_EXPIRED`), ví dụ sau khi đổi mật khẩu ở máy khác.
     * `SessionManager` đăng xuất với lý do `SESSION_REVOKED` (spec 011 R9).
     */
    data object SessionRevoked : AppError("Session revoked")

    /** Lỗi còn lại. Giữ `cause` để tầng gọi tự phân loại thêm (ví dụ lỗi riêng của Firebase Auth). */
    data class Unknown(override val cause: Throwable?) : AppError(cause?.message, cause)
}

package com.example.booking_app.core.session

import kotlinx.coroutines.flow.StateFlow

/**
 * Phiên đăng nhập dùng chung cho mọi tính năng (contracts/ui-screens.md §4).
 * Lấy `uid` qua [currentUid], không gọi `FirebaseAuth` trong các package `feature`.
 */
interface SessionManager {
    val sessionState: StateFlow<SessionState>

    /** `true` khi `SignedOut`, tức chế độ khách (research R15). */
    val isGuest: StateFlow<Boolean>

    /** Gọi sau `on-signup` hoặc khi vai trò đổi để đọc lại claim. */
    suspend fun refresh(forceTokenRefresh: Boolean = true)

    suspend fun signOut()

    /**
     * [011] Đăng xuất kèm lý do. Có thân mặc định để các fake `SessionManager` cũ vẫn biên dịch;
     * `FirebaseSessionManager` override để chạy `SignOutHook` và gắn lý do vào `SignedOut`.
     */
    suspend fun signOut(reason: SignOutReason?) {
        signOut()
    }

    /** `null` khi chưa `SignedIn`. */
    fun currentUid(): String?

    fun currentAppRole(): AppRole?
}

package com.example.booking_app.core.session

import kotlinx.coroutines.flow.Flow

/** Người dùng Firebase Auth đã rút gọn, để `SessionManager` test được không cần Firebase. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val phone: String?,
    val displayName: String?,
    /** `providerId` của các phương thức đã liên kết, ví dụ `password`, `google.com`, `phone`. */
    val providers: List<String>,
    val isEmailVerified: Boolean,
)

/**
 * Bọc `FirebaseAuth` cho `core/session`. Ngoài `feature/auth/data`, đây là chỗ duy nhất được gọi
 * `FirebaseAuth`. Các hàm ném `AppError` (đã qua `awaitWithTimeout`).
 */
interface SessionAuthSource {
    val authState: Flow<AuthUser?>
    suspend fun getClaims(forceRefresh: Boolean): Map<String, Any?>
    suspend fun reload()
    suspend fun signOut()
}

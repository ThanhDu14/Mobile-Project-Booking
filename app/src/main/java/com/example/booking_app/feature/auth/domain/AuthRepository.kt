package com.example.booking_app.feature.auth.domain

import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.PendingCredential

/**
 * Thao tác với Firebase Auth cho các UseCase của spec 010. Đăng nhập số điện thoại (cần Activity)
 * thêm ở US5.
 */
interface AuthRepository {
    /** Trả `uid`. Nếu lần trước đã tạo xong mà app báo hết giờ thì không tạo lại (research R13). */
    suspend fun signUpWithEmail(email: String, password: String): AuthResult<String>

    suspend fun signInWithEmail(email: String, password: String): AuthResult<Unit>

    suspend fun sendEmailVerification(): AuthResult<Unit>

    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit>

    suspend fun signInWithGoogle(idToken: String): AuthResult<Unit>

    /** Liên kết phương thức đang chờ vào tài khoản vừa đăng nhập (FR-012, research R7). */
    suspend fun linkPendingCredential(pending: PendingCredential): AuthResult<Unit>
}

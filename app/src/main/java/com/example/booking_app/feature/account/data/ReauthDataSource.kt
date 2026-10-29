package com.example.booking_app.feature.account.data

/** Thông tin tài khoản đang đăng nhập đủ để chọn cách xác nhận lại danh tính (research R8). */
data class ReauthAccount(
    /** `providerId` trong `providerData`: `password`, `google.com`, `phone`. */
    val providers: Set<String>,
    val email: String?,
    val phoneE164: String?,
)

/**
 * Xác nhận lại danh tính với Firebase Auth (research R8). Lỗi ném ra là exception gốc của Firebase hoặc `AppError`;
 * `AccountErrorMapper` phân loại ở tầng gọi.
 */
interface ReauthDataSource {
    /** `null` khi đang ở chế độ khách. */
    fun currentAccount(): ReauthAccount?

    suspend fun reauthWithPassword(password: String)

    suspend fun reauthWithGoogle(idToken: String)

    suspend fun reauthWithPhone(verificationId: String, code: String)

    /** Gọi ngay sau `reauthWithPassword` (US5). */
    suspend fun updatePassword(newPassword: String)
}

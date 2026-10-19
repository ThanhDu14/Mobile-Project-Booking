package com.example.booking_app.core.session

/** Thông tin điền sẵn ở bước Hoàn tất tài khoản (Google/OTP lần đầu, research R4). */
data class ConsentPrefill(
    val provider: Provider,
    val email: String?,
    val phone: String?,
    val displayName: String?,
) {
    enum class Provider { GOOGLE, PHONE, PASSWORD }
}

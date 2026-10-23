package com.example.booking_app.feature.auth.domain.model

/** Thông tin đăng nhập đang chờ liên kết (FR-012). Tầng data giữ đối tượng thật của Firebase. */
interface PendingCredential

/** Lỗi của các luồng đăng ký/đăng nhập (data-model §4). Mỗi lỗi có chuỗi tiếng Việt trong `strings_auth.xml`. */
sealed interface AuthError {
    /** Sai email, sai mật khẩu hoặc chưa có tài khoản: cùng một thông báo (FR-011). */
    data object InvalidCredentials : AuthError
    data object EmailInUse : AuthError
    data object AccountLocked : AuthError
    data object InvalidCode : AuthError
    data object CodeExpired : AuthError
    data object TooManyAttempts : AuthError

    /** Hết quota SMS của gói Spark hoặc số không phải số thử nghiệm. */
    data object PhoneNotSupported : AuthError
    data object GoogleCancelled : AuthError
    data object NoGoogleAccount : AuthError

    /** Email đã có tài khoản bằng phương thức khác; cần đăng nhập phương thức cũ rồi liên kết (R7). */
    data class NeedsLinking(val email: String?, val pendingCredential: PendingCredential?) : AuthError
    data object Network : AuthError
    data class Unknown(val cause: Throwable?) : AuthError
}

/** Kết quả của `AuthRepository`. */
sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

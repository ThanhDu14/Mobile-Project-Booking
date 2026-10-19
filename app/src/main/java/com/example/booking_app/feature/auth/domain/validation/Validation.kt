package com.example.booking_app.feature.auth.domain.validation

/** Kết quả kiểm tra một ô nhập. `Valid` mang giá trị đã chuẩn hóa (trim, viết thường, E.164). */
sealed interface Validated<out T> {
    data class Valid<T>(val value: T) : Validated<T>
    data class Invalid(val error: ValidationError) : Validated<Nothing>
}

/** Lỗi kiểm tra dữ liệu nhập (data-model §4). [key] trùng tên chuỗi trong `strings_auth.xml`. */
enum class ValidationError(val key: String) {
    NAME_LENGTH("auth_error_name_length"),
    EMAIL_FORMAT("auth_error_email_format"),
    PASSWORD_RULE("auth_error_password_rule"),
    PASSWORD_MISMATCH("auth_error_password_mismatch"),
    PHONE_FORMAT("auth_error_phone_format"),
    OTP_FORMAT("auth_error_otp_format"),

    /** Đăng nhập: chỉ cần không trống, quy tắc mật khẩu chỉ áp dụng khi đặt mật khẩu. */
    PASSWORD_REQUIRED("auth_error_password_required"),
}

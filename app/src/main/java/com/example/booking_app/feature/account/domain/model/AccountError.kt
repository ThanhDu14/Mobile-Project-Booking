package com.example.booking_app.feature.account.domain.model

/** Lỗi riêng của `feature/account` (data-model §6). Chuỗi hiển thị trong `strings_account.xml`. */
sealed class AccountError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    data object WrongCurrentPassword : AccountError("Wrong current password")

    data object SamePassword : AccountError("New password equals current password")

    data object WeakPassword : AccountError("Weak password")

    data object SameEmail : AccountError("New email equals current email")

    data object ReauthCancelled : AccountError("Reauthentication cancelled")

    data object ReauthWrongAccount : AccountError("Reauthenticated with another account")

    data object ReauthRequired : AccountError("Recent login required")

    /** Sai mã OTP khi xác nhận lại bằng số điện thoại (research R8). */
    data object WrongOtpCode : AccountError("Wrong OTP code")

    /** Mã OTP hết 5 phút hoặc đã sai 5 lần: phải gửi lại mã. */
    data object OtpExpired : AccountError("OTP expired")

    data class ImpactChanged(val newImpact: DeletionImpact) : AccountError("Deletion impact changed")

    data object DeleteTooLarge : AccountError("Too many items to delete")

    data object AdminSelfDelete : AccountError("Admin cannot delete self")

    data object ImageTooLarge : AccountError("Image too large")

    data object UnsupportedImage : AccountError("Unsupported image type")

    data object PermissionDenied : AccountError("Permission denied")

    data class OwnerRevoked(val reason: String?) : AccountError("Owner role revoked")

    data object Network : AccountError("Network unavailable")

    data class Unknown(override val cause: Throwable?) : AccountError(cause?.message, cause)
}

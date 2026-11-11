package com.example.booking_app.core.session

/** Avatar ở header dùng chung của các tab chính (spec 011 US8, FR-028). */
sealed interface AccountAvatarState {
    /** Khách: icon người, bấm mở đăng nhập. */
    data object Guest : AccountAvatarState

    /** Đã đăng nhập nhưng hồ sơ chưa tải: vòng tròn trống, vẫn bấm mở trang Tài khoản. */
    data object Pending : AccountAvatarState

    /**
     * Đã có hồ sơ. `avatarPath` khác `null` thì hiện ảnh (bucket `avatars`), ảnh lỗi thì về `initials`;
     * `initials` rỗng (họ tên rỗng) thì hiện icon người.
     */
    data class SignedIn(val initials: String, val avatarPath: String?) : AccountAvatarState
}

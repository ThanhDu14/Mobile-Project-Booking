package com.example.booking_app.core.session

/**
 * Việc cần làm ngay trước khi đăng xuất, lúc token còn hiệu lực (spec 011 R1, R5).
 *
 * Quy ước: tính năng nào có dữ liệu cần dọn khi đăng xuất thì `@Binds @IntoSet` một hook riêng.
 * `SessionManager.signOut()` chạy từng hook tối đa 5 giây, lỗi bị bỏ qua. Không chạy khi tài khoản
 * vừa bị xóa (`SignOutReason.ACCOUNT_DELETED`) vì token đã hết hiệu lực.
 */
fun interface SignOutHook {
    suspend fun beforeSignOut(uid: String)
}

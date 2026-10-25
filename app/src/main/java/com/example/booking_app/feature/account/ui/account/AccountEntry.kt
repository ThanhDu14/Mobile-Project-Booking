package com.example.booking_app.feature.account.ui.account

import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.core.session.AppRole

/**
 * Các mục trong danh sách của trang Tài khoản (research R14). Mục nào cũng mở bằng deep link để không import
 * `ui/` của tính năng khác (constitution I). Màn hình trong `nav_account` (đổi mật khẩu, đổi email, xóa tài khoản)
 * cũng dùng deep link `smashnow://account/...`: destination còn là Fragment khung thì chưa khai báo deep link,
 * nên mục tự ẩn tới khi story đó làm xong.
 */
enum class AccountEntry(val deepLink: String, @StringRes val label: Int) {
    MY_BOOKINGS("smashnow://bookings/mine", R.string.account_entry_my_bookings),
    MY_DROPINS("smashnow://dropin/mine", R.string.account_entry_my_dropins),
    FAVORITES("smashnow://favorites", R.string.account_entry_favorites),
    NOTIFICATION_SETTINGS("smashnow://notifications/settings", R.string.account_entry_notification_settings),
    CHANGE_PASSWORD("smashnow://account/password", R.string.account_entry_change_password),
    CHANGE_EMAIL("smashnow://account/email", R.string.account_entry_change_email),
    LEGAL("smashnow://legal/TERMS", R.string.account_entry_legal),
    WALLET_TOPUP("smashnow://wallet/topup", R.string.account_wallet_topup),
    DELETE_ACCOUNT("smashnow://account/delete", R.string.account_delete_account),
}

private const val PASSWORD_PROVIDER = "password"

/**
 * Mục được hiện cho người dùng hiện tại. [hasDeepLink] thường là `navController.graph::hasDeepLink` trên graph gốc.
 * - Không có provider `password`: ẩn đổi mật khẩu, đổi email (FR-015).
 * - Admin: ẩn xóa tài khoản (FR-018, research R16).
 * - Deep link chưa có destination: ẩn (R14).
 */
fun accountEntries(
    providers: Collection<String>,
    appRole: AppRole,
    hasDeepLink: (String) -> Boolean,
): Set<AccountEntry> = AccountEntry.entries.filterTo(LinkedHashSet()) { entry ->
    when (entry) {
        AccountEntry.CHANGE_PASSWORD, AccountEntry.CHANGE_EMAIL -> PASSWORD_PROVIDER in providers
        AccountEntry.DELETE_ACCOUNT -> appRole != AppRole.ADMIN
        else -> true
    } && hasDeepLink(entry.deepLink)
}

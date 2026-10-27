package com.example.booking_app.feature.account.ui.account

/**
 * Lối sang màn hình chủ sân của D (spec 110, 111) và C (091) bằng deep link (research R14). Chưa có destination trong
 * graph thì nút tương ứng bị ẩn, trang Tài khoản vẫn hiện chữ trạng thái.
 */
enum class OwnerLink(val deepLink: String) {
    APPLY_NEW("smashnow://owner/apply?mode=new"),
    RESUBMIT("smashnow://owner/apply?mode=resubmit"),
    APPLICATION("smashnow://owner/application"),
    VENUES("smashnow://owner/venues"),
    CHAT("smashnow://chat/owner"),
}

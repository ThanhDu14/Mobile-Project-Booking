package com.example.booking_app.feature.dropin.ui.list

import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bộ lọc đã áp dụng gần nhất trong phiên chạy app. Bấm tab khác rồi bấm lại "Vãng lai" tạo back stack entry mới
 * (SavedStateHandle rỗng), nên danh sách đọc lại bộ lọc ở đây thay vì quay về bộ lọc mặc định từ hồ sơ.
 * Chỉ giữ trong bộ nhớ: mở lại app thì dùng bộ lọc mặc định như AC 1.2.
 */
@Singleton
class DropInFilterMemory @Inject constructor() {
    @Volatile
    var last: DropInFilter? = null
}

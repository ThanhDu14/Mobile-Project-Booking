package com.example.booking_app.feature.payment.domain

/** Giờ dùng để đếm ngược giữ chỗ: giờ máy đã bù lệch với server (`ServerTimeOffsetProvider`). Test thay bằng giờ ảo. */
fun interface HoldClock {
    fun nowMillis(): Long
}

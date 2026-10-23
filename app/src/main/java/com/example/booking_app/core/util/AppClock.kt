package com.example.booking_app.core.util

import android.os.SystemClock
import javax.inject.Inject

/** Đồng hồ thay được trong test, cho các mốc chờ gửi lại (60 giây) và hạn mã OTP (5 phút). */
interface AppClock {
    /** Giờ thật (ms), dùng cho mốc lưu trong DataStore. */
    fun now(): Long

    /** Thời gian từ lúc bật máy (ms), không đổi theo giờ hệ thống; dùng cho phiên OTP (research R8). */
    fun elapsedRealtime(): Long
}

class SystemAppClock @Inject constructor() : AppClock {
    override fun now(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

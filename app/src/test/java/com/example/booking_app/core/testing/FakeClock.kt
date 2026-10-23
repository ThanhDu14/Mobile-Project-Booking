package com.example.booking_app.core.testing

import com.example.booking_app.core.util.AppClock

/** Đồng hồ cho test. Truyền `{ testScheduler.currentTime }` để khớp với thời gian ảo của `delay`. */
class FakeClock(private val time: () -> Long) : AppClock {
    override fun now(): Long = time()
    override fun elapsedRealtime(): Long = time()
}

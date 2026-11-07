package com.example.booking_app.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Số giây còn lại tới [deadline] (làm tròn lên), phát mỗi giây và kết thúc bằng `0`. */
fun countdownSeconds(deadline: Long, now: () -> Long): Flow<Int> = flow {
    while (true) {
        val remaining = deadline - now()
        if (remaining <= 0) {
            emit(0)
            return@flow
        }
        emit(((remaining + 999) / 1000).toInt())
        // Chờ tới đúng mốc giây kế tiếp để số giây không bị lệch dần.
        delay((remaining - 1) % 1000 + 1)
    }
}

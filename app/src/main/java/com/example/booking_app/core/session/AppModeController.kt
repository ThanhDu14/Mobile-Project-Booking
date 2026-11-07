package com.example.booking_app.core.session

import kotlinx.coroutines.flow.StateFlow

/**
 * Chế độ "Người chơi" / "Quản lý sân" của chủ sân, chỉ sống trong phiên dùng app (spec 011 FR-011, R7).
 * `MainActivity` điều hướng theo cặp `(appRole, mode)` qua [rootGraphFor].
 */
interface AppModeController {
    val mode: StateFlow<AppMode>

    /**
     * Chế độ ứng với [state], tính đồng bộ. `MainActivity` dùng hàm này để không điều hướng nhầm
     * vào `nav_player` trong lúc [mode] chưa kịp cập nhật sau khi chủ sân vừa đăng nhập.
     */
    fun modeFor(state: SessionState): AppMode

    /**
     * Sang [AppMode.PLAYER]: đổi ngay, không gọi mạng.
     * Sang [AppMode.OWNER]: làm mới token và kiểm tra claim `OWNER`; quyền đã bị thu hồi thì trả
     * `Result.failure(OwnerRevokedException)` và giữ nguyên chế độ.
     */
    suspend fun switchTo(target: AppMode): Result<AppMode>
}

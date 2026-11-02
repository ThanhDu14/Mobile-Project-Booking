package com.example.booking_app.core.session

import com.example.booking_app.core.di.ApplicationScope
import com.example.booking_app.core.network.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val OWNER_REFRESH_TIMEOUT_MS = 3_000L

/**
 * Giữ chế độ **trong bộ nhớ**, không lưu DataStore (spec 011 FR-011, R7). Mỗi lần chủ sân đăng nhập
 * hoặc mở app, chế độ về [AppMode.OWNER].
 */
@Singleton
class DefaultAppModeController @Inject constructor(
    private val sessionManager: SessionManager,
    @ApplicationScope scope: CoroutineScope,
) : AppModeController {

    /** `uid` của chủ sân đã chọn "Người chơi" trong phiên này; `null` là đang ở mặc định "Quản lý sân". */
    @Volatile
    private var playerChosenBy: String? = null

    private val _mode = MutableStateFlow(modeFor(sessionManager.sessionState.value))
    override val mode: StateFlow<AppMode> = _mode.asStateFlow()

    init {
        scope.launch {
            sessionManager.sessionState.collect { state ->
                // Rời vai trò chủ sân (đăng xuất, bị thu hồi): lần sau thành chủ sân lại về "Quản lý sân".
                if (state.ownerUid() == null) playerChosenBy = null
                _mode.value = modeFor(state)
            }
        }
    }

    override fun modeFor(state: SessionState): AppMode {
        val ownerUid = state.ownerUid() ?: return AppMode.PLAYER
        return if (playerChosenBy == ownerUid) AppMode.PLAYER else AppMode.OWNER
    }

    private fun SessionState.ownerUid(): String? =
        (this as? SessionState.SignedIn)?.takeIf { it.appRole == AppRole.OWNER }?.uid

    override suspend fun switchTo(target: AppMode): Result<AppMode> {
        if (target == AppMode.PLAYER) {
            playerChosenBy = sessionManager.currentUid()
            _mode.value = AppMode.PLAYER
            return Result.success(AppMode.PLAYER)
        }
        if (sessionManager.currentAppRole() != AppRole.OWNER) return Result.failure(OwnerRevokedException())
        try {
            withTimeout(OWNER_REFRESH_TIMEOUT_MS) { sessionManager.refresh(forceTokenRefresh = true) }
        } catch (_: TimeoutCancellationException) {
            // Mạng chậm: dùng claim của token đang có; server vẫn chặn nếu quyền đã bị thu hồi (R7 bước 4).
        } catch (_: AppError) {
            // Mất mạng: như trên.
        }
        if (sessionManager.currentAppRole() != AppRole.OWNER) return Result.failure(OwnerRevokedException())
        playerChosenBy = null
        _mode.value = AppMode.OWNER
        return Result.success(AppMode.OWNER)
    }
}

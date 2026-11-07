package com.example.booking_app.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kênh báo "tài khoản vừa bị khóa" từ tầng mạng (Edge Function trả `403 ACCOUNT_LOCKED`)
 * tới `SessionManager`, để mọi tính năng không phải tự xử lý đăng xuất.
 */
@Singleton
class AccountLockedBus @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun publish() {
        _events.tryEmit(Unit)
    }
}

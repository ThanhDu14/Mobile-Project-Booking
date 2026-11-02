package com.example.booking_app.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kênh báo "tài khoản đã được xóa" từ tầng mạng (Edge Function trả `403 ACCOUNT_DELETING`) tới
 * `SessionManager` (spec 011, contracts/edge-functions-account.md §4). Tách khỏi [AccountLockedBus]
 * để người dùng thấy "Tài khoản đã được xóa", không thấy thông báo khóa.
 */
@Singleton
class AccountDeletedBus @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun publish() {
        _events.tryEmit(Unit)
    }
}

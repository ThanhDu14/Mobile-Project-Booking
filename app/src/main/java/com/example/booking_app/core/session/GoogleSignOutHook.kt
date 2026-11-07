package com.example.booking_app.core.session

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Xóa trạng thái đăng nhập Google của Credential Manager, để lần sau "Tiếp tục với Google" cho chọn lại tài khoản
 * (research R5). Tài khoản không dùng Google thì lời gọi không làm gì. Lỗi đã được `SessionManager` bỏ qua.
 */
class GoogleSignOutHook @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SignOutHook {
    override suspend fun beforeSignOut(uid: String) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}

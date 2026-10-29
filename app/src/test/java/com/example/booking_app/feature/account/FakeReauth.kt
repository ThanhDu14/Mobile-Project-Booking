package com.example.booking_app.feature.account

import com.example.booking_app.core.network.IdTokenProvider
import com.example.booking_app.feature.account.data.ReauthAccount
import com.example.booking_app.feature.account.data.ReauthDataSource

/** Ghi lại lời gọi dạng `"password:<mk>"`, `"google:<token>"`, `"phone:<vid>:<code>"`, `"update:<mk>"`. */
class FakeReauthDataSource : ReauthDataSource {
    var account: ReauthAccount? = ReauthAccount(providers = setOf("password"), email = "a@b.vn", phoneE164 = null)
    val calls = mutableListOf<String>()

    /** Lỗi ném ra ở lần xác nhận lại danh tính kế tiếp. */
    var failure: Throwable? = null

    /** Lỗi ném ra ở `updatePassword`. */
    var updateFailure: Throwable? = null

    /** Đặt để treo `updatePassword` tới khi test cho chạy tiếp. */
    var updateGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun currentAccount(): ReauthAccount? = account

    override suspend fun reauthWithPassword(password: String) = record("password:$password")

    override suspend fun reauthWithGoogle(idToken: String) = record("google:$idToken")

    override suspend fun reauthWithPhone(verificationId: String, code: String) = record("phone:$verificationId:$code")

    override suspend fun updatePassword(newPassword: String) {
        calls += "update:$newPassword"
        updateGate?.await()
        updateFailure?.let { throw it }
    }

    private fun record(call: String) {
        calls += call
        failure?.let { throw it }
    }
}

class FakeIdTokenProvider : IdTokenProvider {
    val forceRefreshCalls = mutableListOf<Boolean>()

    override suspend fun getToken(forceRefresh: Boolean): String {
        forceRefreshCalls += forceRefresh
        return "token"
    }
}

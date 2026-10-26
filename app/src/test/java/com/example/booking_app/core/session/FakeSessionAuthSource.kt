package com.example.booking_app.core.session

import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionAuthSource : SessionAuthSource {
    override val authState = MutableStateFlow<AuthUser?>(null)

    /** Claim trả về theo `forceRefresh`. Ném lỗi để giả lập mất mạng hoặc tài khoản bị khóa. */
    var claims: (forceRefresh: Boolean) -> Map<String, Any?> = { emptyMap() }
    var reloadError: Throwable? = null

    /** Người dùng sau `reload()` (ví dụ vừa xác minh email). `null` thì trả người dùng hiện tại. */
    var reloadedUser: AuthUser? = null

    val claimCalls = mutableListOf<Boolean>()
    var reloadCalls = 0
    var signOutCalls = 0

    override suspend fun getClaims(forceRefresh: Boolean): Map<String, Any?> {
        claimCalls += forceRefresh
        return claims(forceRefresh)
    }

    override suspend fun reload(): AuthUser? {
        reloadCalls++
        reloadError?.let { throw it }
        return reloadedUser ?: authState.value
    }

    override suspend fun signOut() {
        signOutCalls++
        authState.value = null
    }
}

class FakeSessionPrefs : SessionPrefs {
    override val welcomeSeen = MutableStateFlow(false)
    var pending: PendingSignup? = null
    var lastVerificationEmailAt: Long? = null
    var lastPasswordResetAt: Long? = null
    var clearOnSignOutCalls = 0

    override suspend fun setWelcomeSeen() { welcomeSeen.value = true }
    override suspend fun pendingSignup() = pending
    override suspend fun savePendingSignup(pendingSignup: PendingSignup) { pending = pendingSignup }
    override suspend fun clearPendingSignup() { pending = null }
    override suspend fun lastVerificationEmailAt() = lastVerificationEmailAt
    override suspend fun setLastVerificationEmailAt(at: Long) { lastVerificationEmailAt = at }
    override suspend fun lastPasswordResetAt() = lastPasswordResetAt
    override suspend fun setLastPasswordResetAt(at: Long) { lastPasswordResetAt = at }
    override suspend fun clearLastPasswordResetAt() { lastPasswordResetAt = null }

    override suspend fun clearOnSignOut() {
        clearOnSignOutCalls++
        pending = null
        lastVerificationEmailAt = null
    }
}

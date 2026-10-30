package com.example.booking_app.core.session

import com.example.booking_app.core.network.AppError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FcmSignOutHookTest {

    private val log = mutableListOf<String>()
    private val tokens = FakeFcmTokenSource(log)
    private val store = FakeFcmTokenStore(log)
    private val hook = FcmSignOutHook(tokens, store)

    @Test
    fun `go token khoi users roi xoa token cua may`() = runTest {
        hook.beforeSignOut("u1")
        assertEquals(listOf("token", "remove:u1:tok-1", "deleteToken"), log)
    }

    @Test
    fun `ghi Firestore loi (mat mang) thi van xoa token`() = runTest {
        store.error = AppError.Network
        hook.beforeSignOut("u1")
        assertEquals(listOf("token", "remove:u1:tok-1", "deleteToken"), log)
    }

    @Test
    fun `lay token loi thi khong nem ra ngoai, van xoa token`() = runTest {
        tokens.tokenError = IllegalStateException("SERVICE_NOT_AVAILABLE")
        hook.beforeSignOut("u1")
        assertEquals(listOf("token", "deleteToken"), log)
    }

    @Test
    fun `xoa token loi cung khong nem ra ngoai`() = runTest {
        tokens.deleteError = IllegalStateException("boom")
        hook.beforeSignOut("u1")
        assertEquals(listOf("token", "remove:u1:tok-1", "deleteToken"), log)
    }
}

private class FakeFcmTokenSource(private val log: MutableList<String>) : FcmTokenSource {
    var tokenError: Throwable? = null
    var deleteError: Throwable? = null

    override suspend fun token(): String {
        log += "token"
        tokenError?.let { throw it }
        return "tok-1"
    }

    override suspend fun deleteToken() {
        log += "deleteToken"
        deleteError?.let { throw it }
    }
}

private class FakeFcmTokenStore(private val log: MutableList<String>) : FcmTokenStore {
    var error: Throwable? = null

    override suspend fun removeToken(uid: String, token: String) {
        log += "remove:$uid:$token"
        error?.let { throw it }
    }
}

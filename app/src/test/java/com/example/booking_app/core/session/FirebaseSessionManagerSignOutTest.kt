package com.example.booking_app.core.session

import com.example.booking_app.core.network.AccountDeletedBus
import com.example.booking_app.core.network.AccountLockedBus
import com.example.booking_app.core.network.AppError
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseSessionManagerSignOutTest {

    private val authSource = FakeSessionAuthSource()
    private val prefs = FakeSessionPrefs()
    private val profiles = MutableSharedFlow<MyProfile?>()
    private val profileReader = object : UserProfileReader {
        override fun observeMyProfile() = profiles
    }
    private val deletedBus = AccountDeletedBus()

    /** Thứ tự gọi: tên hook kèm uid, và "auth.signOut". */
    private val calls = mutableListOf<String>()

    private val user = AuthUser(
        uid = "u1",
        email = "an@example.com",
        phone = null,
        displayName = "Nguyễn An",
        providers = listOf("password"),
        isEmailVerified = true,
    )

    private fun hook(name: String, block: suspend () -> Unit = {}) = SignOutHook { uid ->
        calls += "$name:$uid"
        block()
    }

    private fun TestScope.createManager(vararg hooks: SignOutHook): FirebaseSessionManager {
        authSource.claims = { mapOf("appRole" to "PLAYER") }
        authSource.authState.value = user
        val recordingSource = object : SessionAuthSource by authSource {
            override suspend fun signOut() {
                calls += "auth.signOut"
                authSource.signOut()
            }
        }
        val manager = FirebaseSessionManager(
            recordingSource, prefs, profileReader, AccountLockedBus(), backgroundScope,
            signOutHooks = hooks.toSet(), deletedBus = deletedBus,
        )
        runCurrent()
        return manager
    }

    @Test
    fun `signOut goi moi hook voi uid hien tai truoc khi dang xuat Firebase`() = runTest {
        val manager = createManager(hook("a"), hook("b"))

        manager.signOut()

        assertEquals(3, calls.size)
        assertEquals(setOf("a:u1", "b:u1"), calls.take(2).toSet())
        assertEquals("auth.signOut", calls.last())
        assertEquals(SessionState.SignedOut(null), manager.sessionState.value)
    }

    @Test
    fun `hook nem loi thi hook con lai van chay va van dang xuat`() = runTest {
        val manager = createManager(hook("loi") { throw IllegalStateException("x") }, hook("b"))

        manager.signOut()

        assertTrue("b:u1" in calls)
        assertEquals("auth.signOut", calls.last())
        assertEquals(SessionState.SignedOut(null), manager.sessionState.value)
    }

    @Test
    fun `hook chay qua 5 giay bi huy va van dang xuat`() = runTest {
        val manager = createManager(hook("treo") { awaitCancellation() })

        val start = testScheduler.currentTime
        manager.signOut()

        assertEquals(5_000L, testScheduler.currentTime - start)
        assertEquals("auth.signOut", calls.last())
        assertEquals(SessionState.SignedOut(null), manager.sessionState.value)
    }

    @Test
    fun `signOut ACCOUNT_DELETED khong goi hook`() = runTest {
        val manager = createManager(hook("a"))

        manager.signOut(SignOutReason.ACCOUNT_DELETED)
        runCurrent()

        assertEquals(listOf("auth.signOut"), calls)
        assertEquals(SessionState.SignedOut(SignOutReason.ACCOUNT_DELETED), manager.sessionState.value)
    }

    @Test
    fun `AccountDeletedBus phat su kien thi SignedOut ACCOUNT_DELETED va khong goi hook`() = runTest {
        val manager = createManager(hook("a"))

        deletedBus.publish()
        runCurrent()

        assertEquals(listOf("auth.signOut"), calls)
        assertEquals(SessionState.SignedOut(SignOutReason.ACCOUNT_DELETED), manager.sessionState.value)
    }

    @Test
    fun `token bi thu hoi khi lam moi thi SignedOut SESSION_REVOKED va khong goi hook`() = runTest {
        val manager = createManager(hook("a"))
        authSource.claims = { throw AppError.SessionRevoked }

        manager.refresh(forceTokenRefresh = true)
        runCurrent()

        assertEquals(listOf("auth.signOut"), calls)
        assertEquals(SessionState.SignedOut(SignOutReason.SESSION_REVOKED), manager.sessionState.value)
    }

    @Test
    fun `token bi thu hoi khi mo app thi SignedOut SESSION_REVOKED`() = runTest {
        authSource.reloadError = AppError.SessionRevoked
        val manager = createManager(hook("a"))

        assertEquals(SessionState.SignedOut(SignOutReason.SESSION_REVOKED), manager.sessionState.value)
        assertEquals(listOf("auth.signOut"), calls)
    }

    @Test
    fun `signOut van xoa du lieu phien nhu 010`() = runTest {
        prefs.pending = PendingSignup(uid = "u1", name = "Nguyễn An", termsAt = 1L)
        prefs.lastVerificationEmailAt = 10L
        authSource.claims = { mapOf("appRole" to "PLAYER") }
        val manager = createManager(hook("a"))

        manager.signOut()

        assertEquals(1, prefs.clearOnSignOutCalls)
        assertNull(prefs.pending)
        assertNull(prefs.lastVerificationEmailAt)
    }
}

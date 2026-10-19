package com.example.booking_app.core.session

import com.example.booking_app.core.network.AccountLockedBus
import com.example.booking_app.core.network.AppError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseSessionManagerTest {

    private val authSource = FakeSessionAuthSource()
    private val prefs = FakeSessionPrefs()
    private val profiles = MutableSharedFlow<MyProfile?>()
    private val profileReader = object : UserProfileReader {
        override fun observeMyProfile() = profiles
    }
    private val lockedBus = AccountLockedBus()

    private val googleUser = AuthUser(
        uid = "u1",
        email = "an@example.com",
        phone = null,
        displayName = "Nguyễn An",
        providers = listOf("google.com"),
        isEmailVerified = true,
    )

    private fun TestScope.createManager(): FirebaseSessionManager {
        val manager = FirebaseSessionManager(authSource, prefs, profileReader, lockedBus, backgroundScope)
        runCurrent()
        return manager
    }

    private fun profile(role: String = "PLAYER", accountStatus: String = "ACTIVE") = MyProfile(
        displayName = "Nguyễn An",
        level = null,
        favoriteDistrictIds = emptyList(),
        avatarPath = null,
        role = role,
        accountStatus = accountStatus,
    )

    @Test
    fun `khong co nguoi dung thi SignedOut va la khach`() = runTest {
        val manager = createManager()
        assertEquals(SessionState.SignedOut(reason = null), manager.sessionState.value)
        assertTrue(manager.isGuest.value)
        assertNull(manager.currentUid())
        assertNull(manager.currentAppRole())
    }

    @Test
    fun `chua co claim appRole va co PendingSignup cung uid thi Initializing`() = runTest {
        prefs.pending = PendingSignup(uid = "u1", name = "Nguyễn An", termsAt = 1L)
        authSource.authState.value = googleUser
        val manager = createManager()
        assertEquals(SessionState.Initializing, manager.sessionState.value)
        assertNull(manager.currentUid())
    }

    @Test
    fun `chua co claim va khong co PendingSignup thi NeedsConsent dien san tu Firebase`() = runTest {
        authSource.authState.value = googleUser
        val manager = createManager()
        assertEquals(
            SessionState.NeedsConsent(
                ConsentPrefill(
                    provider = ConsentPrefill.Provider.GOOGLE,
                    email = "an@example.com",
                    phone = null,
                    displayName = "Nguyễn An",
                ),
            ),
            manager.sessionState.value,
        )
    }

    @Test
    fun `PendingSignup cua uid khac bi xoa`() = runTest {
        prefs.pending = PendingSignup(uid = "other", name = "Bình", termsAt = 1L)
        authSource.authState.value = googleUser.copy(providers = listOf("phone"), phone = "+84901234567")
        val manager = createManager()
        assertNull(prefs.pending)
        val state = manager.sessionState.value as SessionState.NeedsConsent
        assertEquals(ConsentPrefill.Provider.PHONE, state.prefill.provider)
        assertEquals("+84901234567", state.prefill.phone)
    }

    @Test
    fun `claim appRole quyet dinh vai tro, gia tri la coi la PLAYER`() = runTest {
        for ((claim, role) in listOf("PLAYER" to AppRole.PLAYER, "OWNER" to AppRole.OWNER, "ADMIN" to AppRole.ADMIN, "SUPERUSER" to AppRole.PLAYER)) {
            authSource.claims = { mapOf("appRole" to claim) }
            authSource.authState.value = null
            authSource.authState.value = googleUser
            val manager = createManager()
            assertEquals(SessionState.SignedIn("u1", role, emailVerified = true), manager.sessionState.value)
            assertFalse(manager.isGuest.value)
            assertEquals("u1", manager.currentUid())
            assertEquals(role, manager.currentAppRole())
        }
    }

    @Test
    fun `khi mo app goi reload va lam moi token`() = runTest {
        authSource.claims = { mapOf("appRole" to "PLAYER") }
        authSource.authState.value = googleUser
        createManager()
        assertEquals(1, authSource.reloadCalls)
        assertEquals(listOf(true), authSource.claimCalls)
    }

    @Test
    fun `tai khoan bi tat khi mo app thi dang xuat voi ly do LOCKED`() = runTest {
        authSource.reloadError = AppError.Locked
        authSource.authState.value = googleUser
        val manager = createManager()
        assertEquals(1, authSource.signOutCalls)
        assertEquals(SessionState.SignedOut(SignOutReason.LOCKED), manager.sessionState.value)
        assertTrue(manager.isGuest.value)
    }

    @Test
    fun `mat mang khi mo app thi dung claim cua token da luu`() = runTest {
        authSource.reloadError = AppError.Network
        authSource.claims = { force -> if (force) throw AppError.Network else mapOf("appRole" to "OWNER") }
        authSource.authState.value = googleUser
        val manager = createManager()
        assertEquals(SessionState.SignedIn("u1", AppRole.OWNER, emailVerified = true), manager.sessionState.value)
        assertEquals(false, authSource.claimCalls.last())
    }

    @Test
    fun `accountStatus LOCKED tren document thi dang xuat ngay`() = runTest {
        authSource.claims = { mapOf("appRole" to "PLAYER") }
        authSource.authState.value = googleUser
        val manager = createManager()
        profiles.emit(profile(accountStatus = "LOCKED"))
        runCurrent()
        assertEquals(1, authSource.signOutCalls)
        assertEquals(SessionState.SignedOut(SignOutReason.LOCKED), manager.sessionState.value)
    }

    @Test
    fun `role tren document khac appRole thi lam moi token va doi vai tro`() = runTest {
        var appRole = "PLAYER"
        authSource.claims = { mapOf("appRole" to appRole) }
        authSource.authState.value = googleUser
        val manager = createManager()
        appRole = "OWNER"
        profiles.emit(profile(role = "OWNER"))
        runCurrent()
        assertEquals(listOf(true, true), authSource.claimCalls)
        assertEquals(SessionState.SignedIn("u1", AppRole.OWNER, emailVerified = true), manager.sessionState.value)
    }

    @Test
    fun `role tren document trung appRole thi khong lam moi token`() = runTest {
        authSource.claims = { mapOf("appRole" to "PLAYER") }
        authSource.authState.value = googleUser
        createManager()
        profiles.emit(profile(role = "PLAYER"))
        runCurrent()
        assertEquals(listOf(true), authSource.claimCalls)
    }

    @Test
    fun `AccountLockedBus phat su kien thi SignedOut LOCKED`() = runTest {
        authSource.claims = { mapOf("appRole" to "PLAYER") }
        authSource.authState.value = googleUser
        val manager = createManager()
        lockedBus.publish()
        runCurrent()
        assertEquals(SessionState.SignedOut(SignOutReason.LOCKED), manager.sessionState.value)
    }

    @Test
    fun `signOut xoa du lieu phien nhung giu welcome_seen`() = runTest {
        authSource.claims = { mapOf("appRole" to "PLAYER") }
        authSource.authState.value = googleUser
        prefs.welcomeSeen.value = true
        prefs.lastVerificationEmailAt = 10L
        val manager = createManager()
        manager.signOut()
        runCurrent()
        assertEquals(1, prefs.clearOnSignOutCalls)
        assertNull(prefs.lastVerificationEmailAt)
        assertTrue(prefs.welcomeSeen.value)
        assertEquals(SessionState.SignedOut(reason = null), manager.sessionState.value)
        assertNull(manager.currentUid())
        assertTrue(manager.isGuest.value)
    }

    @Test
    fun `refresh sau khi khoi tao xong thi SignedIn`() = runTest {
        prefs.pending = PendingSignup(uid = "u1", name = "Nguyễn An", termsAt = 1L)
        authSource.authState.value = googleUser
        val manager = createManager()
        assertEquals(SessionState.Initializing, manager.sessionState.value)
        authSource.claims = { mapOf("appRole" to "PLAYER") }
        manager.refresh()
        runCurrent()
        assertEquals(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true), manager.sessionState.value)
    }
}

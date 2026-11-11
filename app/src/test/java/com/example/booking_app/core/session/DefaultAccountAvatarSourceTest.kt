package com.example.booking_app.core.session

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 011 US8, FR-028: trạng thái avatar ở header dùng chung. */
class DefaultAccountAvatarSourceTest {

    private val session = FakeSessionManager(SessionState.SignedOut(null))

    /** Chưa `emit` gì = hồ sơ đang tải. */
    private val profile = MutableSharedFlow<MyProfile?>(replay = 1)
    private val reader = object : UserProfileReader {
        override fun observeMyProfile(): Flow<MyProfile?> = profile
    }
    private val source = DefaultAccountAvatarSource(session, reader)

    private fun profileOf(name: String, avatarPath: String? = null) = MyProfile(
        displayName = name, level = null, favoriteDistrictIds = emptyList(),
        avatarPath = avatarPath, role = "PLAYER", accountStatus = "ACTIVE",
    )

    private fun signIn() {
        session.sessionState.value = SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true)
    }

    @Test
    fun `khach thay Guest`() = runTest {
        source.state.test { assertEquals(AccountAvatarState.Guest, awaitItem()) }
    }

    @Test
    fun `dang nhap ma ho so chua tai thi Pending`() = runTest {
        signIn()
        source.state.test { assertEquals(AccountAvatarState.Pending, awaitItem()) }
    }

    @Test
    fun `khong co anh thi chu cai dau, co anh thi kem duong dan`() = runTest {
        signIn()
        profile.tryEmit(profileOf("Lê Hoàng Nam"))
        source.state.test {
            assertEquals(AccountAvatarState.Pending, awaitItem())
            assertEquals(AccountAvatarState.SignedIn("HN", null), awaitItem())
            profile.tryEmit(profileOf("Lê Hoàng Nam", "u1/avatar_1.jpg"))
            assertEquals(AccountAvatarState.SignedIn("HN", "u1/avatar_1.jpg"), awaitItem())
        }
    }

    @Test
    fun `doi ten cap nhat chu cai dau, dang xuat ve Guest`() = runTest {
        signIn()
        profile.tryEmit(profileOf("An"))
        source.state.test {
            assertEquals(AccountAvatarState.Pending, awaitItem())
            assertEquals(AccountAvatarState.SignedIn("A", null), awaitItem())
            profile.tryEmit(profileOf("Trần Bình"))
            assertEquals(AccountAvatarState.SignedIn("TB", null), awaitItem())
            session.sessionState.value = SessionState.SignedOut(null)
            assertEquals(AccountAvatarState.Guest, awaitItem())
        }
    }

    @Test
    fun `ho so null khi da dang nhap thi Pending`() = runTest {
        signIn()
        profile.tryEmit(null)
        source.state.test { assertEquals(AccountAvatarState.Pending, awaitItem()) }
    }

    @Test
    fun `ho ten rong thi SignedIn khong chu`() = runTest {
        signIn()
        profile.tryEmit(profileOf("   "))
        source.state.test {
            assertEquals(AccountAvatarState.Pending, awaitItem())
            assertEquals(AccountAvatarState.SignedIn("", null), awaitItem())
        }
    }

    @Test
    fun `initialsOf lay hai tu cuoi`() {
        assertEquals("HN", initialsOf("Lê Hoàng Nam"))
        assertEquals("A", initialsOf(" an "))
        assertEquals("", initialsOf("  "))
    }
}

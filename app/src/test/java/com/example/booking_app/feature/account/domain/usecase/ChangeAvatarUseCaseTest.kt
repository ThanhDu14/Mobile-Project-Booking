package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.account.CallLog
import com.example.booking_app.feature.account.FakeAccountRepository
import com.example.booking_app.feature.account.FakeAvatarRepository
import com.example.booking_app.feature.account.domain.model.AccountError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChangeAvatarUseCaseTest {

    private val log = CallLog()
    private val avatars = FakeAvatarRepository(log)
    private val account = FakeAccountRepository(log).apply { avatarPath = OLD }
    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true))
    private val clock = FakeClock { NOW }
    private val changeAvatar = ChangeAvatarUseCase(avatars, account, session, clock)
    private val removeAvatar = RemoveAvatarUseCase(avatars, account)

    @Test
    fun `thanh cong - tai len, ghi avatarPath, xoa anh cu theo dung thu tu`() = runTest {
        val path = changeAvatar(ByteArray(3), OLD)
        assertEquals(NEW, path)
        assertEquals(listOf("upload:$NEW", "setAvatarPath:$NEW", "delete:$OLD"), log)
        assertEquals(NEW, account.avatarPath)
    }

    @Test
    fun `loi xoa anh cu bi bo qua`() = runTest {
        avatars.deleteError = AccountError.Network
        assertEquals(NEW, changeAvatar(ByteArray(3), OLD))
        assertEquals(NEW, account.avatarPath)
    }

    @Test
    fun `chua co anh cu thi khong xoa gi`() = runTest {
        changeAvatar(ByteArray(3), null)
        assertEquals(listOf("upload:$NEW", "setAvatarPath:$NEW"), log)
    }

    @Test
    fun `loi tai len - khong ghi Firestore, avatarPath giu nguyen`() = runTest {
        avatars.uploadError = AccountError.Network
        try {
            changeAvatar(ByteArray(3), OLD)
            fail("phải ném lỗi")
        } catch (e: AccountError) {
            assertEquals(AccountError.Network, e)
        }
        assertEquals(listOf("upload:$NEW"), log)
        assertEquals(OLD, account.avatarPath)
    }

    @Test
    fun `loi ghi Firestore - xoa object moi vua tai (bo qua loi), tra loi`() = runTest {
        account.setAvatarError = AccountError.Network
        avatars.deleteError = AccountError.Network
        try {
            changeAvatar(ByteArray(3), OLD)
            fail("phải ném lỗi")
        } catch (e: AccountError) {
            assertEquals(AccountError.Network, e)
        }
        assertEquals(listOf("upload:$NEW", "setAvatarPath:$NEW", "delete:$NEW"), log)
        assertEquals(OLD, account.avatarPath)
    }

    @Test
    fun `xoa anh - ghi null roi xoa object, loi xoa bi bo qua`() = runTest {
        avatars.deleteError = AccountError.Network
        removeAvatar(OLD)
        assertEquals(listOf("setAvatarPath:null", "delete:$OLD"), log)
        assertNull(account.avatarPath)
    }

    @Test
    fun `xoa anh - loi ghi Firestore thi khong xoa object`() = runTest {
        account.setAvatarError = AccountError.Network
        try {
            removeAvatar(OLD)
            fail("phải ném lỗi")
        } catch (e: AccountError) {
            assertTrue(e is AccountError.Network)
        }
        assertEquals(listOf("setAvatarPath:null"), log)
    }

    private companion object {
        const val NOW = 1_760_500_000_000L
        const val OLD = "u1/avatar_1760000000000.jpg"
        const val NEW = "u1/avatar_1760500000000.jpg"
    }
}

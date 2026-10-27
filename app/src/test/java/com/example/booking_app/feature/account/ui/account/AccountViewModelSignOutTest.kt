package com.example.booking_app.feature.account.ui.account

import app.cash.turbine.test
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionAuthSource
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.accountViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelSignOutTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true))
    private val reader = object : UserProfileReader {
        override fun observeMyProfile() = flowOf<MyProfile?>(null)
    }
    private val vm by lazy { accountViewModel(session, FakeSessionAuthSource(), reader) }

    @Test
    fun `bam Dang xuat thi mo hop thoai xac nhan, chua dang xuat`() = runTest(mainRule.dispatcher) {
        vm.events.test {
            vm.onSignOutClick()
            runCurrent()
            assertEquals(AccountEvent.ConfirmSignOut, awaitItem())
        }
        assertEquals(0, session.signOutCalls)
    }

    @Test
    fun `xac nhan thi goi signOut dung 1 lan, bam lap khong goi them`() = runTest(mainRule.dispatcher) {
        vm.signOut()
        vm.signOut()
        vm.signOut()
        advanceUntilIdle()
        assertEquals(1, session.signOutCalls)
    }
}

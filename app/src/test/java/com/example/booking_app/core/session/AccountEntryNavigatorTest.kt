package com.example.booking_app.core.session

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import com.example.booking_app.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/** Spec 011 US8, FR-029, FR-030: bấm avatar ở header dùng chung. */
class AccountEntryNavigatorTest {

    private val session = FakeSessionManager(SessionState.SignedOut(null))
    private val authNavigator = mockk<AuthNavigator>(relaxed = true)
    private val navigator = DefaultAccountEntryNavigator(session, authNavigator)
    private val navController = mockk<NavController>(relaxed = true)

    private fun at(destinationId: Int, parentId: Int = R.id.nav_player) {
        val graph = mockk<NavGraph> { every { id } returns parentId }
        val destination = mockk<NavDestination> {
            every { id } returns destinationId
            every { parent } returns graph
        }
        every { navController.currentDestination } returns destination
    }

    @Test
    fun `khach bam avatar thi mo dang nhap, khong returnTo`() {
        at(R.id.playerHomePlaceholderFragment)
        navigator.open(navController)
        verify(exactly = 1) { authNavigator.openLogin(navController, null as Uri?) }
        verify(exactly = 0) { navController.navigate(R.id.action_global_account, any(), any()) }
    }

    @Test
    fun `da dang nhap bam avatar thi mo trang Tai khoan`() {
        session.sessionState.value = SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true)
        at(R.id.playerHomePlaceholderFragment)
        navigator.open(navController)
        verify(exactly = 1) { navController.navigate(R.id.action_global_account, null, any()) }
        verify(exactly = 0) { authNavigator.openLogin(any(), any()) }
    }

    @Test
    fun `dang o trang Tai khoan thi khong mo chong`() {
        session.sessionState.value = SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true)
        at(R.id.accountFragment, parentId = R.id.nav_account)
        navigator.open(navController)
        verify(exactly = 0) { navController.navigate(any<Int>(), any(), any()) }
    }

    @Test
    fun `khach dang o man dang nhap thi khong mo chong`() {
        at(R.id.loginFragment, parentId = R.id.nav_auth)
        navigator.open(navController)
        verify(exactly = 0) { authNavigator.openLogin(any(), any()) }
    }
}

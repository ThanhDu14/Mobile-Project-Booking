package com.example.booking_app.feature.account.ui.account

import app.cash.turbine.test
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.AuthUser
import com.example.booking_app.core.session.FakeSessionAuthSource
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeDistrictRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true))
    private val authSource = FakeSessionAuthSource().apply {
        authState.value = AuthUser("u1", "an@example.com", null, "An", listOf("password"), isEmailVerified = true)
    }
    private val profile = MutableStateFlow<MyProfile?>(null)
    private val reader = object : UserProfileReader {
        override fun observeMyProfile() = profile
    }

    private fun viewModel() = AccountViewModel(session, authSource, reader, FakeDistrictRepository())

    private fun myProfile() = MyProfile(
        displayName = "Nguyễn An", level = null, favoriteDistrictIds = emptyList(), avatarPath = null,
        role = "PLAYER", accountStatus = "ACTIVE", email = "an@example.com",
    )

    @Test
    fun `chua co ho so thi dang tai, co ho so thi Content kem muc theo deep link da co`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.setAvailableDeepLinks(setOf(AccountEntry.LEGAL.deepLink, AccountEntry.CHANGE_PASSWORD.deepLink))
        vm.uiState.test {
            assertEquals(AccountUiState.Loading, awaitItem())
            profile.value = myProfile()
            val content = awaitItem() as AccountUiState.Content
            assertEquals("Nguyễn An", content.profile.displayName)
            assertEquals(setOf(AccountEntry.LEGAL, AccountEntry.CHANGE_PASSWORD), content.entries)
        }
    }

    @Test
    fun `qua 15 giay khong co ho so thi Error, Thu lai thi tai lai`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(AccountUiState.Loading, awaitItem())
            advanceTimeBy(15_001)
            assertEquals(AccountUiState.Error(AppError.Timeout), awaitItem())
            vm.retry()
            assertEquals(AccountUiState.Loading, awaitItem())
            profile.value = myProfile()
            assertTrue(awaitItem() is AccountUiState.Content)
        }
    }

    @Test
    fun `bam muc thi phat su kien mo dung muc`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.events.test {
            vm.onEntryClick(AccountEntry.LEGAL)
            runCurrent()
            assertEquals(AccountEvent.Open(AccountEntry.LEGAL), awaitItem())
        }
    }
}

package com.example.booking_app.feature.account.ui.account

import app.cash.turbine.test
import com.example.booking_app.core.session.AppMode
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.AuthUser
import com.example.booking_app.core.session.FakeSessionAuthSource
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.OwnerRevokedException
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeAppModeController
import com.example.booking_app.feature.account.accountViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Khối "Chế độ đang dùng" của chủ sân (US4, FR-010–FR-012). */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelModeTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.OWNER, emailVerified = true))
    private val authSource = FakeSessionAuthSource().apply {
        authState.value = AuthUser("u1", "duc@example.com", null, "Đức", listOf("password"), isEmailVerified = true)
    }
    private val profile = MutableStateFlow<MyProfile?>(ownerProfile())
    private val reader = object : UserProfileReader {
        override fun observeMyProfile() = profile
    }
    private val modeController = FakeAppModeController(AppMode.OWNER)

    private fun viewModel() = accountViewModel(session, authSource, reader, modeController)

    private fun ownerProfile(ownerStatus: String = "APPROVED", revokedReason: String? = null) = MyProfile(
        displayName = "Huỳnh Đức", level = null, favoriteDistrictIds = emptyList(), avatarPath = null,
        role = "OWNER", accountStatus = "ACTIVE", email = "duc@example.com",
        ownerStatus = ownerStatus, ownerRevokedReason = revokedReason,
    )

    @Test
    fun `chu san thay khoi chuyen che do dung che do hien tai`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            assertEquals(AppMode.OWNER, (expectMostRecentItem() as AccountUiState.Content).mode)
        }
    }

    @Test
    fun `chon Nguoi choi thi goi switchTo PLAYER va toggle doi theo`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            vm.onModeSelected(AppMode.PLAYER)
            runCurrent()
            assertEquals(listOf(AppMode.PLAYER), modeController.switchCalls)
            assertEquals(AppMode.PLAYER, (expectMostRecentItem() as AccountUiState.Content).mode)
        }
    }

    @Test
    fun `chon che do dang dung thi khong goi switchTo`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            vm.onModeSelected(AppMode.OWNER)
            runCurrent()
            assertEquals(emptyList<AppMode>(), modeController.switchCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chon Quan ly san khi quyen bi thu hoi thi o lai Nguoi choi va bao ly do (US4-4, FR-012)`() =
        runTest(mainRule.dispatcher) {
            modeController.mode.value = AppMode.PLAYER
            modeController.result = { Result.failure(OwnerRevokedException()) }
            profile.value = ownerProfile("NONE", "Nhiều báo cáo thông tin sai sự thật")
            val vm = viewModel()
            vm.uiState.test {
                runCurrent()
                vm.events.test {
                    vm.onModeSelected(AppMode.OWNER)
                    runCurrent()
                    assertEquals(AccountEvent.OwnerRevoked("Nhiều báo cáo thông tin sai sự thật"), awaitItem())
                }
                assertEquals(AppMode.PLAYER, (expectMostRecentItem() as AccountUiState.Content).mode)
            }
        }

    @Test
    fun `tai khoan khong phai OWNER thi khong co khoi chuyen che do (US4-3)`() = runTest(mainRule.dispatcher) {
        session.sessionState.value = SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true)
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            assertNull((expectMostRecentItem() as AccountUiState.Content).mode)
        }
    }
}

package com.example.booking_app.feature.account.ui.account

import app.cash.turbine.test
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.AuthUser
import com.example.booking_app.core.session.FakeSessionAuthSource
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeAppModeController
import com.example.booking_app.feature.account.FakeDistrictRepository
import com.example.booking_app.feature.account.FakeOwnerApplicationRepository
import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import com.example.booking_app.feature.account.domain.model.OwnerSection
import com.example.booking_app.feature.account.domain.usecase.SwitchModeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Mục chủ sân trên trang Tài khoản (US3, FR-008, FR-009). */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelOwnerTest {

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
    private val applications = FakeOwnerApplicationRepository()
    private val modeController = FakeAppModeController()

    private fun viewModel() = AccountViewModel(
        session, authSource, reader, FakeDistrictRepository(), applications, modeController,
        SwitchModeUseCase(modeController),
    ).apply { setAvailableDeepLinks(OwnerLink.entries.map { it.deepLink }.toSet()) }

    private fun myProfile(ownerStatus: String = "NONE", revokedReason: String? = null) = MyProfile(
        displayName = "Nguyễn An", level = null, favoriteDistrictIds = emptyList(), avatarPath = null,
        role = "PLAYER", accountStatus = "ACTIVE", email = "an@example.com",
        ownerStatus = ownerStatus, ownerRevokedReason = revokedReason,
    )

    private val submittedAt = Instant.parse("2026-10-20T03:00:00Z")

    @Test
    fun `NONE thi khong mo listener ho so, muc la Dang ky lam chu san`() = runTest(mainRule.dispatcher) {
        profile.value = myProfile()
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            val content = expectMostRecentItem() as AccountUiState.Content
            assertEquals(OwnerSection.CanApply, content.ownerSection)
            assertEquals(0, applications.openedCount)
        }
    }

    @Test
    fun `PENDING thi mo listener va hien ngay nop, doi trang thai thi dong listener`() = runTest(mainRule.dispatcher) {
        profile.value = myProfile("PENDING")
        applications.latest.value = OwnerApplicationSummary("PENDING", submittedAt, null)
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            assertEquals(OwnerSection.Pending(submittedAt), (expectMostRecentItem() as AccountUiState.Content).ownerSection)
            assertEquals(1, applications.activeListeners)

            profile.value = myProfile("NONE")
            runCurrent()
            assertEquals(OwnerSection.CanApply, (expectMostRecentItem() as AccountUiState.Content).ownerSection)
            assertEquals(0, applications.activeListeners)
        }
    }

    @Test
    fun `REJECTED thi hien ly do tu choi tu ho so gan nhat`() = runTest(mainRule.dispatcher) {
        profile.value = myProfile("REJECTED")
        applications.latest.value = OwnerApplicationSummary("REJECTED", submittedAt, "Ảnh giấy phép bị mờ")
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            val content = expectMostRecentItem() as AccountUiState.Content
            assertEquals(OwnerSection.Rejected("Ảnh giấy phép bị mờ"), content.ownerSection)
        }
    }

    @Test
    fun `Rules chan doc ho so thi van hien trang thai, khong dang xuat`() = runTest(mainRule.dispatcher) {
        profile.value = myProfile("PENDING")
        applications.error = AccountError.PermissionDenied
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            assertEquals(OwnerSection.Pending(null), (expectMostRecentItem() as AccountUiState.Content).ownerSection)
        }
        assertEquals(0, session.signOutCalls)
    }

    @Test
    fun `admin duyet khi dang mo trang thi muc doi sang Approved ma khong dang xuat (US3-5)`() = runTest(mainRule.dispatcher) {
        profile.value = myProfile("PENDING")
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            assertEquals(OwnerSection.Pending(null), (expectMostRecentItem() as AccountUiState.Content).ownerSection)

            session.sessionState.value = SessionState.SignedIn("u1", AppRole.OWNER, emailVerified = true)
            profile.value = myProfile("APPROVED")
            runCurrent()
            assertEquals(OwnerSection.Approved, (expectMostRecentItem() as AccountUiState.Content).ownerSection)
        }
        assertEquals(0, session.signOutCalls)
    }

    @Test
    fun `bam Sua va nop lai khi bi tu choi thi mo deep link mode=resubmit`() = runTest(mainRule.dispatcher) {
        profile.value = myProfile("REJECTED")
        val vm = viewModel()
        vm.uiState.test {
            runCurrent()
            vm.events.test {
                vm.onOwnerActionClick()
                runCurrent()
                assertEquals(AccountEvent.OpenLink("smashnow://owner/apply?mode=resubmit"), awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Dang ky lam chu san, ke ca sau khi bi thu hoi, mo deep link mode=new`() = runTest(mainRule.dispatcher) {
        listOf(myProfile(), myProfile(revokedReason = "Báo cáo sai sự thật")).forEach { p ->
            profile.value = p
            val vm = viewModel()
            vm.uiState.test {
                runCurrent()
                vm.events.test {
                    vm.onOwnerActionClick()
                    runCurrent()
                    assertEquals(AccountEvent.OpenLink("smashnow://owner/apply?mode=new"), awaitItem())
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `deep link chu san chua co destination thi khong co trong ownerLinks (R14)`() = runTest(mainRule.dispatcher) {
        profile.value = myProfile()
        val vm = viewModel()
        vm.setAvailableDeepLinks(emptySet())
        vm.uiState.test {
            runCurrent()
            assertEquals(emptySet<OwnerLink>(), (expectMostRecentItem() as AccountUiState.Content).ownerLinks)
        }
    }
}

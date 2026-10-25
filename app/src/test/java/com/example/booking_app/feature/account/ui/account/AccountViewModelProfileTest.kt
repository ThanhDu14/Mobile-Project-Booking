package com.example.booking_app.feature.account.ui.account

import com.example.booking_app.R
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.AuthUser
import com.example.booking_app.core.session.FakeSessionAuthSource
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeDistrictRepository
import com.example.booking_app.feature.account.domain.model.PlayerLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelProfileTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = false))
    private val authSource = FakeSessionAuthSource().apply {
        authState.value = AuthUser("u1", "nam@example.com", null, "Nam", listOf("password"), isEmailVerified = false)
    }
    private val profile = MutableStateFlow<MyProfile?>(
        MyProfile(
            displayName = "Lê Hoàng Nam", level = "INTERMEDIATE",
            favoriteDistrictIds = listOf("q10", "khong-con", "q-cu", "q3"), avatarPath = null,
            role = "PLAYER", accountStatus = "ACTIVE", email = "nam@example.com", walletBalance = 50_000,
        ),
    )
    private val reader = object : UserProfileReader {
        override fun observeMyProfile() = profile
    }

    private fun TestScope.content(): AccountUiState.Content {
        val vm = AccountViewModel(session, authSource, reader, FakeDistrictRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value as AccountUiState.Content
    }

    @Test
    fun `ten khu vuc theo thu tu favoriteDistrictIds, ID khong con thi bo qua`() = runTest(mainRule.dispatcher) {
        assertEquals(listOf("Quận 10", "Quận cũ", "Quận 3"), content().districtNames)
    }

    @Test
    fun `trinh do va nhan hien thi`() = runTest(mainRule.dispatcher) {
        assertEquals(PlayerLevel.INTERMEDIATE, content().level)
        assertEquals(R.string.account_level_intermediate, levelLabel(PlayerLevel.INTERMEDIATE))
        assertEquals(R.string.account_level_beginner, levelLabel(PlayerLevel.BEGINNER))
        assertEquals(R.string.account_level_advanced, levelLabel(PlayerLevel.ADVANCED))
    }

    @Test
    fun `so du vi dinh dang 50_000`() = runTest(mainRule.dispatcher) {
        assertEquals("50.000", content().walletBalance)
        assertEquals("0", formatVnd(0))
        assertEquals("1.250.000", formatVnd(1_250_000))
        assertEquals("999", formatVnd(999))
    }

    @Test
    fun `banner xac minh khi co provider password va chua xac minh`() = runTest(mainRule.dispatcher) {
        assertTrue(content().showVerifyBanner)
    }

    @Test
    fun `da xac minh hoac khong co password thi khong hien banner`() = runTest(mainRule.dispatcher) {
        session.sessionState.value = SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true)
        assertFalse(content().showVerifyBanner)
    }

    @Test
    fun `tai khoan Google khong hien banner`() = runTest(mainRule.dispatcher) {
        authSource.authState.value = AuthUser("u1", "nam@gmail.com", null, "Nam", listOf("google.com"), false)
        assertFalse(content().showVerifyBanner)
    }

    @Test
    fun `chua chon trinh do thi level null`() = runTest(mainRule.dispatcher) {
        profile.value = profile.value!!.copy(level = null)
        assertNull(content().level)
    }

    @Test
    fun `chu cai dau theo hai tu cuoi`() {
        assertEquals("HN", initialsOf("Lê Hoàng Nam"))
        assertEquals("A", initialsOf(" an "))
        assertEquals("", initialsOf("  "))
    }
}

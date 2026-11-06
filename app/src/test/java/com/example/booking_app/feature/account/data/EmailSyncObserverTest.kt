package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.account.FakeAccountFunctionsApi
import com.example.booking_app.feature.account.FakeReauthDataSource
import com.example.booking_app.feature.account.domain.usecase.SyncAccountEmailUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** T094: đồng bộ `users.email` khi email trong Firebase Auth khác hồ sơ (spec 011 US7, research R10). */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailSyncObserverTest {

    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true))
    private val profile = MutableStateFlow<MyProfile?>(profile("cu@example.com"))
    private val reader = object : UserProfileReader {
        override fun observeMyProfile() = profile
    }
    private val reauth = FakeReauthDataSource().apply { account = account!!.copy(email = "Moi@Example.com") }
    private val api = FakeAccountFunctionsApi()

    private fun TestScope.start() {
        EmailSyncObserver(session, reader, reauth, SyncAccountEmailUseCase(api)).start(backgroundScope)
        runCurrent()
    }

    @Test
    fun `email trong Auth khac ho so thi goi sync dung 1 lan cho moi gia tri email`() = runTest {
        start()
        assertEquals(listOf("sync"), api.calls)

        // Hồ sơ phát lại (vd đổi tên) mà email Auth vẫn vậy: không gọi thêm.
        profile.value = profile("cu@example.com").copy(displayName = "Tên mới")
        runCurrent()
        assertEquals(listOf("sync"), api.calls)

        // Server đã ghi email mới: hồ sơ khớp, không gọi.
        profile.value = profile("moi@example.com")
        runCurrent()
        assertEquals(listOf("sync"), api.calls)

        // Đổi email lần nữa: gọi thêm đúng 1 lần.
        reauth.account = reauth.account!!.copy(email = "lan2@example.com")
        profile.value = profile("moi@example.com").copy(displayName = "x")
        runCurrent()
        assertEquals(listOf("sync", "sync"), api.calls)
    }

    @Test
    fun `email giong ho so thi khong goi`() = runTest {
        reauth.account = reauth.account!!.copy(email = "cu@example.com")
        start()
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun `chua dang nhap thi khong goi`() = runTest {
        session.sessionState.value = SessionState.SignedOut(null)
        start()
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun `loi khi goi sync thi bo qua, khong crash va van theo doi tiep`() = runTest {
        api.syncResult = { throw AppError.Network }
        start()
        assertEquals(listOf("sync"), api.calls)

        api.syncResult = { true }
        reauth.account = reauth.account!!.copy(email = "khac@example.com")
        profile.value = profile("cu@example.com").copy(displayName = "y")
        runCurrent()
        assertEquals(listOf("sync", "sync"), api.calls)
    }

    private fun profile(email: String) = MyProfile(
        displayName = "Nam", level = null, favoriteDistrictIds = emptyList(), avatarPath = null,
        role = "PLAYER", accountStatus = "ACTIVE", email = email,
    )
}

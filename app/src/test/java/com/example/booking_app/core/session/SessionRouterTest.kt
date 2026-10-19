package com.example.booking_app.core.session

import android.net.Uri
import com.example.booking_app.core.network.AppError
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRouterTest {

    private val prefill = ConsentPrefill(ConsentPrefill.Provider.GOOGLE, "an@example.com", null, "An")

    @Test
    fun `dang tai thi o lai man hinh khoi dong`() {
        assertNull(SessionRouter.startDestination(SessionState.Loading, welcomeSeen = false))
    }

    @Test
    fun `khach chua xem man hinh chao thi vao Welcome`() {
        assertEquals(RouteDecision.Welcome, SessionRouter.startDestination(SessionState.SignedOut(null), welcomeSeen = false))
    }

    @Test
    fun `khach da xem man hinh chao thi vao trang chu che do khach`() {
        assertEquals(
            RouteDecision.PlayerGuest(showLockedLogin = false),
            SessionRouter.startDestination(SessionState.SignedOut(null), welcomeSeen = true),
        )
    }

    @Test
    fun `bi khoa thi vao trang chu khach roi mo dang nhap, du chua xem man hinh chao`() {
        for (seen in listOf(false, true)) {
            assertEquals(
                RouteDecision.PlayerGuest(showLockedLogin = true),
                SessionRouter.startDestination(SessionState.SignedOut(SignOutReason.LOCKED), welcomeSeen = seen),
            )
        }
    }

    @Test
    fun `da dang nhap thi vao graph theo vai tro`() {
        for (role in AppRole.entries) {
            for (seen in listOf(false, true)) {
                assertEquals(
                    RouteDecision.RoleHome(role),
                    SessionRouter.startDestination(SessionState.SignedIn("u1", role, emailVerified = true), welcomeSeen = seen),
                )
            }
        }
    }

    @Test
    fun `trang thai khoi tao di toi man hinh tuong ung`() {
        assertEquals(RouteDecision.Consent, SessionRouter.startDestination(SessionState.NeedsConsent(prefill), welcomeSeen = true))
        assertEquals(RouteDecision.AccountSetup, SessionRouter.startDestination(SessionState.Initializing, welcomeSeen = true))
        assertEquals(
            RouteDecision.AccountSetup,
            SessionRouter.startDestination(SessionState.InitFailed(AppError.Network), welcomeSeen = false),
        )
    }

    @Test
    fun `PLAYER dang nhap khi co nav_player ben duoi thi pop nav_auth`() {
        val returnTo = mockk<Uri>()
        assertEquals(
            RouteDecision.PopAuth(returnTo),
            SessionRouter.afterSignIn(AppRole.PLAYER, hasPlayerGraphBelowAuth = true, returnTo = returnTo),
        )
        assertEquals(
            RouteDecision.PopAuth(null),
            SessionRouter.afterSignIn(AppRole.PLAYER, hasPlayerGraphBelowAuth = true, returnTo = null),
        )
    }

    @Test
    fun `PLAYER dang nhap tu man hinh chao thi dat lai ve nav_player`() {
        assertEquals(
            RouteDecision.ResetTo(AppRole.PLAYER),
            SessionRouter.afterSignIn(AppRole.PLAYER, hasPlayerGraphBelowAuth = false, returnTo = null),
        )
    }

    @Test
    fun `OWNER va ADMIN luon dat lai ve graph cua vai tro`() {
        for (role in listOf(AppRole.OWNER, AppRole.ADMIN)) {
            for (below in listOf(false, true)) {
                assertEquals(
                    RouteDecision.ResetTo(role),
                    SessionRouter.afterSignIn(role, hasPlayerGraphBelowAuth = below, returnTo = mockk()),
                )
            }
        }
    }
}

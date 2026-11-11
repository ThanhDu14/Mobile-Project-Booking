package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.ConsentPrefill
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.courtdetail.domain.model.PendingAction
import com.example.booking_app.feature.courtdetail.domain.model.PendingDecision
import org.junit.Assert.assertEquals
import org.junit.Test

/** Bảng quyết định ở data-model §2.4 (research R11). */
class PendingActionResolverTest {

    private val signedIn = SessionState.SignedIn("player1", AppRole.PLAYER, true)
    private val signedOut = SessionState.SignedOut(null)

    private fun resolve(
        state: SessionState,
        pending: PendingAction?,
        ownerId: String? = "owner-1",
        uid: String? = (state as? SessionState.SignedIn)?.uid,
        visible: Boolean = true,
    ) = PendingActionResolver.resolve(state, pending, ownerId, uid, visible)

    @Test
    fun `khong co thao tac cho thi Wait`() {
        assertEquals(PendingDecision.Wait, resolve(signedIn, null))
        assertEquals(PendingDecision.Wait, resolve(signedOut, null))
        assertEquals(PendingDecision.Wait, resolve(signedIn, null, visible = false))
    }

    @Test
    fun `chu co so dat san hoac chat thi RejectOwnVenue`() {
        assertEquals(PendingDecision.RejectOwnVenue, resolve(signedIn, PendingAction.BOOK, ownerId = "player1"))
        assertEquals(PendingDecision.RejectOwnVenue, resolve(signedIn, PendingAction.CHAT, ownerId = "player1"))
    }

    @Test
    fun `chu co so van luu yeu thich duoc`() {
        assertEquals(
            PendingDecision.Run(PendingAction.FAVORITE),
            resolve(signedIn, PendingAction.FAVORITE, ownerId = "player1"),
        )
    }

    @Test
    fun `da dang nhap con lai thi Run dung thao tac`() {
        PendingAction.entries.forEach { action ->
            assertEquals(PendingDecision.Run(action), resolve(signedIn, action))
        }
    }

    @Test
    fun `van la khach khi man hinh hien lai thi Cancel`() {
        PendingAction.entries.forEach { action ->
            assertEquals(PendingDecision.Cancel, resolve(signedOut, action))
        }
    }

    @Test
    fun `man hinh khong hien thi Wait`() {
        assertEquals(PendingDecision.Wait, resolve(signedIn, PendingAction.BOOK, visible = false))
        assertEquals(PendingDecision.Wait, resolve(signedOut, PendingAction.BOOK, visible = false))
    }

    @Test
    fun `phien chua xong thi Wait`() {
        val pendingStates = listOf(
            SessionState.Loading,
            SessionState.Initializing,
            SessionState.NeedsConsent(ConsentPrefill(ConsentPrefill.Provider.GOOGLE, null, null, null)),
        )
        pendingStates.forEach { state ->
            assertEquals(state.toString(), PendingDecision.Wait, resolve(state, PendingAction.BOOK, uid = null))
        }
    }
}

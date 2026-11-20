package com.example.booking_app.core.testing

import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import io.mockk.every
import kotlinx.coroutines.flow.MutableStateFlow

/** `sessionState` khớp `currentUid()` đã giả lập (đọc lúc ViewModel thu thập): có uid → SignedIn, null → SignedOut. */
fun SessionManager.stubSessionFromUid() {
    every { sessionState } answers {
        MutableStateFlow(currentUid()?.let { SessionState.SignedIn(it, AppRole.PLAYER, true) } ?: SessionState.SignedOut(null))
    }
}

package com.example.booking_app.core.session

import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionManager(initial: SessionState = SessionState.SignedOut(null)) : SessionManager {
    override val sessionState = MutableStateFlow(initial)
    override val isGuest = MutableStateFlow(initial is SessionState.SignedOut)
    val refreshCalls = mutableListOf<Boolean>()
    var signOutCalls = 0

    override suspend fun refresh(forceTokenRefresh: Boolean) { refreshCalls += forceTokenRefresh }
    override suspend fun signOut() {
        signOutCalls++
        sessionState.value = SessionState.SignedOut(null)
    }
    override fun currentUid(): String? = (sessionState.value as? SessionState.SignedIn)?.uid
    override fun currentAppRole(): AppRole? = (sessionState.value as? SessionState.SignedIn)?.appRole
}

package com.example.booking_app.core.session

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.awaitWithTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSessionAuthSource @Inject constructor(
    private val auth: FirebaseAuth,
) : SessionAuthSource {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.conflate()

    override suspend fun getClaims(forceRefresh: Boolean): Map<String, Any?> {
        val user = auth.currentUser ?: throw AppError.NotSignedIn
        return user.getIdToken(forceRefresh).awaitWithTimeout().claims
    }

    override suspend fun reload(): AuthUser? {
        val user = auth.currentUser ?: throw AppError.NotSignedIn
        user.reload().awaitWithTimeout()
        return auth.currentUser?.toAuthUser()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        phone = phoneNumber,
        displayName = displayName,
        providers = providerData.map { it.providerId },
        isEmailVerified = isEmailVerified,
    )
}

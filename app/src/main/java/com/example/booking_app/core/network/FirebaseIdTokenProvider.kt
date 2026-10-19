package com.example.booking_app.core.network

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseIdTokenProvider @Inject constructor(
    private val auth: FirebaseAuth,
) : IdTokenProvider {
    override suspend fun getToken(forceRefresh: Boolean): String {
        val user = auth.currentUser ?: throw AppError.NotSignedIn
        return user.getIdToken(forceRefresh).awaitWithTimeout().token ?: throw AppError.NotSignedIn
    }
}

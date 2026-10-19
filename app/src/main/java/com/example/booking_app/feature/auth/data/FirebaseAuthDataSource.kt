package com.example.booking_app.feature.auth.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.awaitWithTimeout
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject

/** Bọc `FirebaseAuth`; mọi lời gọi qua `awaitWithTimeout` (15 giây, FR-021). */
class FirebaseAuthDataSource @Inject constructor(
    private val auth: FirebaseAuth,
) {
    val currentEmail: String? get() = auth.currentUser?.email

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun createUser(email: String, password: String): String =
        auth.createUserWithEmailAndPassword(email, password).awaitWithTimeout().user?.uid
            ?: throw AppError.NotSignedIn

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).awaitWithTimeout()
    }

    suspend fun sendEmailVerification() {
        val user = auth.currentUser ?: throw AppError.NotSignedIn
        user.sendEmailVerification().awaitWithTimeout()
    }

    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email).awaitWithTimeout()
    }

    suspend fun signInWithGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitWithTimeout()
    }

    suspend fun link(credential: AuthCredential) {
        val user = auth.currentUser ?: throw AppError.NotSignedIn
        user.linkWithCredential(credential).awaitWithTimeout()
    }
}

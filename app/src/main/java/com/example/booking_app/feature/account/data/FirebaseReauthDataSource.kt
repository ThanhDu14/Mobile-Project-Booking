package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.awaitWithTimeout
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthProvider
import javax.inject.Inject

/** Chỗ duy nhất trong `feature/account` gọi `FirebaseAuth` (T024). Mọi lời gọi chờ tối đa 15 giây. */
class FirebaseReauthDataSource @Inject constructor(
    private val auth: FirebaseAuth,
) : ReauthDataSource {

    override fun currentAccount(): ReauthAccount? {
        val user = auth.currentUser ?: return null
        return ReauthAccount(
            providers = user.providerData.map { it.providerId }.toSet(),
            email = user.email,
            phoneE164 = user.phoneNumber,
        )
    }

    override suspend fun reauthWithPassword(password: String) {
        val user = requireUser()
        val email = user.email ?: throw AppError.NotSignedIn
        reauth(user, EmailAuthProvider.getCredential(email, password))
    }

    override suspend fun reauthWithGoogle(idToken: String) {
        reauth(requireUser(), GoogleAuthProvider.getCredential(idToken, null))
    }

    override suspend fun reauthWithPhone(verificationId: String, code: String) {
        reauth(requireUser(), PhoneAuthProvider.getCredential(verificationId, code))
    }

    override suspend fun updatePassword(newPassword: String) {
        requireUser().updatePassword(newPassword).awaitWithTimeout()
    }

    override suspend fun verifyBeforeUpdateEmail(newEmail: String) {
        try {
            requireUser().verifyBeforeUpdateEmail(newEmail).awaitWithTimeout()
        } catch (_: FirebaseAuthUserCollisionException) {
            // Project chưa bật chống dò email thì Firebase báo email đã dùng: coi như đã gửi (US7-2).
        }
    }

    private suspend fun reauth(user: FirebaseUser, credential: AuthCredential) {
        user.reauthenticate(credential).awaitWithTimeout()
    }

    private fun requireUser(): FirebaseUser = auth.currentUser ?: throw AppError.NotSignedIn
}

package com.example.booking_app.feature.auth.data

import android.app.Activity
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.network.toAppError
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Sự kiện của `verifyPhoneNumber`. */
sealed interface PhoneVerification {
    data class CodeSent(val verificationId: String, val token: PhoneAuthProvider.ForceResendingToken) : PhoneVerification

    /** Máy tự đọc được mã hoặc xác minh tức thì; đăng nhập bằng [credential] luôn. */
    data class Completed(val credential: PhoneAuthCredential) : PhoneVerification
}

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

    /**
     * Gửi mã OTP (research R8): `PhoneAuthOptions` timeout 60 giây, gắn với [activity]. Lỗi đóng flow bằng
     * `FirebaseAuthException` (để `AuthErrorMapper` phân loại) hoặc `AppError`.
     */
    fun verifyPhoneNumber(
        activity: Activity,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
    ): Flow<PhoneVerification> = callbackFlow {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                trySend(PhoneVerification.CodeSent(verificationId, token))
            }

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                trySend(PhoneVerification.Completed(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                close(if (e is FirebaseAuthException) e else e.toAppError())
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneE164)
            .setTimeout(PHONE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { resendToken?.let(::setForceResendingToken) }
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        awaitClose()
    }

    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).awaitWithTimeout()
    }

    suspend fun signInWithPhoneCode(verificationId: String, code: String) {
        signInWithPhoneCredential(PhoneAuthProvider.getCredential(verificationId, code))
    }

    companion object {
        const val PHONE_TIMEOUT_SECONDS = 60L
    }
}

package com.example.booking_app.feature.auth

import android.app.Activity
import android.os.Parcelable
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSendResult
import com.example.booking_app.feature.auth.domain.model.PendingCredential
import kotlinx.coroutines.CompletableDeferred

/** Ghi lại lời gọi; [gate] giữ lời gọi đăng nhập/đăng ký treo để test bấm hai lần. */
class FakeAuthRepository : AuthRepository {
    var signUpResult: AuthResult<String> = AuthResult.Success("uid")
    var signInResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var googleResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var linkResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var verificationResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var resetResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var sendOtpResult: AuthResult<OtpSendResult> = AuthResult.Success(OtpSendResult.CodeSent("verification-id", null))
    var verifyOtpResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var gate: CompletableDeferred<Unit>? = null

    /** Tên các lời gọi theo thứ tự, để kiểm tra trình tự của UseCase. */
    val calls = mutableListOf<String>()
    val signUpCalls = mutableListOf<Pair<String, String>>()
    val signInCalls = mutableListOf<Pair<String, String>>()
    val googleCalls = mutableListOf<String>()
    val linkCalls = mutableListOf<PendingCredential>()
    val resetCalls = mutableListOf<String>()
    var verificationCalls = 0
    val sendOtpCalls = mutableListOf<Pair<String, Parcelable?>>()
    val verifyOtpCalls = mutableListOf<Pair<String, String>>()

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult<String> {
        calls += "signUp"
        signUpCalls += email to password
        gate?.await()
        return signUpResult
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<Unit> {
        calls += "signIn"
        signInCalls += email to password
        gate?.await()
        return signInResult
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> {
        calls += "sendEmailVerification"
        verificationCalls++
        return verificationResult
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        calls += "sendPasswordReset"
        resetCalls += email
        gate?.await()
        return resetResult
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<Unit> {
        calls += "google"
        googleCalls += idToken
        gate?.await()
        return googleResult
    }

    override suspend fun linkPendingCredential(pending: PendingCredential): AuthResult<Unit> {
        calls += "link"
        linkCalls += pending
        return linkResult
    }

    override suspend fun sendOtp(activity: Activity, phoneE164: String, resendToken: Parcelable?): AuthResult<OtpSendResult> {
        calls += "sendOtp"
        sendOtpCalls += phoneE164 to resendToken
        gate?.await()
        return sendOtpResult
    }

    override suspend fun verifyOtp(verificationId: String, code: String): AuthResult<Unit> {
        calls += "verifyOtp"
        verifyOtpCalls += verificationId to code
        gate?.await()
        return verifyOtpResult
    }
}

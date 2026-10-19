package com.example.booking_app.feature.auth

import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.PendingCredential
import kotlinx.coroutines.CompletableDeferred

/** Ghi lại lời gọi; [gate] giữ lời gọi đăng nhập treo để test bấm hai lần. */
class FakeAuthRepository : AuthRepository {
    var signInResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var googleResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var linkResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var gate: CompletableDeferred<Unit>? = null

    val signInCalls = mutableListOf<Pair<String, String>>()
    val googleCalls = mutableListOf<String>()
    val linkCalls = mutableListOf<PendingCredential>()

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult<String> = AuthResult.Success("uid")

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<Unit> {
        signInCalls += email to password
        gate?.await()
        return signInResult
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = AuthResult.Success(Unit)

    override suspend fun signInWithGoogle(idToken: String): AuthResult<Unit> {
        googleCalls += idToken
        gate?.await()
        return googleResult
    }

    override suspend fun linkPendingCredential(pending: PendingCredential): AuthResult<Unit> {
        linkCalls += pending
        return linkResult
    }
}

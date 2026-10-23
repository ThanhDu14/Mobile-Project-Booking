package com.example.booking_app.feature.auth.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorMapperTest {

    @Test
    fun `sai email, sai mat khau va khong co tai khoan la cung mot loi`() {
        val errors = listOf(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS"),
            FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "wrong"),
            FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "not found"),
            FirebaseAuthException("ERROR_INTERNAL_ERROR", "INVALID_LOGIN_CREDENTIALS"),
        )
        for (e in errors) assertEquals(AuthError.InvalidCredentials, AuthErrorMapper.map(e))
    }

    @Test
    fun `tai khoan bi tat la AccountLocked`() {
        assertEquals(AuthError.AccountLocked, AuthErrorMapper.map(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "x")))
        assertEquals(AuthError.AccountLocked, AuthErrorMapper.map(AppError.Locked))
    }

    @Test
    fun `email da dung la EmailInUse`() {
        assertEquals(
            AuthError.EmailInUse,
            AuthErrorMapper.map(FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x")),
        )
    }

    @Test
    fun `loi ma OTP`() {
        assertEquals(
            AuthError.InvalidCode,
            AuthErrorMapper.map(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "x")),
        )
        assertEquals(
            AuthError.CodeExpired,
            AuthErrorMapper.map(FirebaseAuthInvalidCredentialsException("ERROR_SESSION_EXPIRED", "x")),
        )
    }

    @Test
    fun `qua nhieu lan thu`() {
        assertEquals(AuthError.TooManyAttempts, AuthErrorMapper.map(FirebaseTooManyRequestsException("x")))
        assertEquals(AuthError.TooManyAttempts, AuthErrorMapper.map(AppError.RateLimited))
    }

    @Test
    fun `het quota SMS hoac chua bat thanh toan thi khong gui duoc ma`() {
        assertEquals(AuthError.PhoneNotSupported, AuthErrorMapper.map(FirebaseAuthException("ERROR_QUOTA_EXCEEDED", "x")))
        assertEquals(
            AuthError.PhoneNotSupported,
            AuthErrorMapper.map(FirebaseAuthException("ERROR_INTERNAL_ERROR", "BILLING_NOT_ENABLED")),
        )
    }

    @Test
    fun `email da co voi phuong thuc khac thi can lien ket`() {
        val error = AuthErrorMapper.map(
            FirebaseAuthUserCollisionException("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "x"),
        )
        assertTrue(error is AuthError.NeedsLinking)
    }

    @Test
    fun `loi cua Firebase da boc trong AppError Unknown van doc duoc`() {
        val wrapped = AppError.Unknown(FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x"))
        assertEquals(AuthError.EmailInUse, AuthErrorMapper.map(wrapped))
    }

    @Test
    fun `Google bi huy hoac khong co tai khoan Google`() {
        assertEquals(AuthError.GoogleCancelled, AuthErrorMapper.map(GetCredentialCancellationException()))
        assertEquals(AuthError.NoGoogleAccount, AuthErrorMapper.map(NoCredentialException()))
    }

    @Test
    fun `mat mang va het thoi gian la Network`() {
        assertEquals(AuthError.Network, AuthErrorMapper.map(AppError.Network))
        assertEquals(AuthError.Network, AuthErrorMapper.map(AppError.Timeout))
    }

    @Test
    fun `loi con lai la Unknown`() {
        val cause = IllegalStateException("boom")
        assertEquals(AuthError.Unknown(cause), AuthErrorMapper.map(cause))
    }
}

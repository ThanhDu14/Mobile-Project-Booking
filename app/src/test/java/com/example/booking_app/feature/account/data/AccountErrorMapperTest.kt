package com.example.booking_app.feature.account.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountErrorMapperTest {

    @Test
    fun `sai mat khau hien tai la WrongCurrentPassword`() {
        val errors = listOf(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS"),
            FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "wrong"),
            FirebaseAuthException("ERROR_INTERNAL_ERROR", "INVALID_LOGIN_CREDENTIALS"),
        )
        for (e in errors) assertEquals(AccountError.WrongCurrentPassword, AccountErrorMapper.map(e))
    }

    @Test
    fun `mat khau moi bi password policy tu choi la WeakPassword`() {
        assertEquals(
            AccountError.WeakPassword,
            AccountErrorMapper.map(FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "too short")),
        )
        assertEquals(
            AccountError.WeakPassword,
            AccountErrorMapper.map(FirebaseAuthException("ERROR_INTERNAL_ERROR", "PASSWORD_DOES_NOT_MEET_REQUIREMENTS")),
        )
    }

    @Test
    fun `qua nhieu lan thu la AppError RateLimited`() {
        assertEquals(AppError.RateLimited, AccountErrorMapper.map(FirebaseTooManyRequestsException("TOO_MANY_ATTEMPTS_TRY_LATER")))
        assertEquals(AppError.RateLimited, AccountErrorMapper.map(FirebaseAuthException("ERROR_INTERNAL_ERROR", "TOO_MANY_ATTEMPTS_TRY_LATER")))
        assertEquals(AppError.RateLimited, AccountErrorMapper.map(AppError.RateLimited))
        assertEquals(AppError.RateLimited, AccountErrorMapper.map(AuthError.TooManyAttempts))
    }

    @Test
    fun `huy hop thoai Google hoac OTP la ReauthCancelled`() {
        assertEquals(AccountError.ReauthCancelled, AccountErrorMapper.map(GetCredentialCancellationException()))
        assertEquals(AccountError.ReauthCancelled, AccountErrorMapper.map(AuthError.GoogleCancelled))
        assertEquals(AccountError.ReauthCancelled, AccountErrorMapper.map(AccountError.ReauthCancelled))
    }

    @Test
    fun `chon tai khoan Google khac la ReauthWrongAccount`() {
        assertEquals(
            AccountError.ReauthWrongAccount,
            AccountErrorMapper.map(FirebaseAuthInvalidUserException("ERROR_USER_MISMATCH", "mismatch")),
        )
        assertEquals(AccountError.ReauthWrongAccount, AccountErrorMapper.map(NoCredentialException()))
    }

    @Test
    fun `can dang nhap gan day la ReauthRequired`() {
        assertEquals(
            AccountError.ReauthRequired,
            AccountErrorMapper.map(FirebaseAuthException("ERROR_REQUIRES_RECENT_LOGIN", "recent")),
        )
    }

    @Test
    fun `sai ma OTP va ma OTP het han`() {
        assertEquals(
            AccountError.WrongOtpCode,
            AccountErrorMapper.map(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "x")),
        )
        assertEquals(
            AccountError.OtpExpired,
            AccountErrorMapper.map(FirebaseAuthInvalidCredentialsException("ERROR_SESSION_EXPIRED", "x")),
        )
        assertEquals(AccountError.OtpExpired, AccountErrorMapper.map(AuthError.CodeExpired))
    }

    @Test
    fun `mat mang la Network`() {
        assertEquals(AccountError.Network, AccountErrorMapper.map(FirebaseNetworkException("offline")))
        assertEquals(AccountError.Network, AccountErrorMapper.map(AppError.Network))
        assertEquals(AccountError.Network, AccountErrorMapper.map(AppError.Timeout))
        assertEquals(AccountError.Network, AccountErrorMapper.map(AuthError.Network))
    }

    @Test
    fun `loi Firebase da boc trong AppError Unknown van doc duoc`() {
        val wrapped = AppError.Unknown(FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "x"))
        assertEquals(AccountError.WrongCurrentPassword, AccountErrorMapper.map(wrapped))
    }

    @Test
    fun `loi phien de SessionManager xu ly thi giu nguyen`() {
        assertEquals(AppError.Locked, AccountErrorMapper.map(AppError.Locked))
        assertEquals(AppError.SessionRevoked, AccountErrorMapper.map(AppError.SessionRevoked))
        assertEquals(AppError.NotSignedIn, AccountErrorMapper.map(AppError.NotSignedIn))
    }

    @Test
    fun `loi con lai la Unknown`() {
        val cause = IllegalStateException("boom")
        assertEquals(AccountError.Unknown(cause), AccountErrorMapper.map(cause))
        assertTrue(AccountErrorMapper.map(FirebaseAuthException("ERROR_INTERNAL_ERROR", "x")) is AccountError.Unknown)
    }
}

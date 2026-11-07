package com.example.booking_app.feature.auth.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.booking_app.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Lấy Google ID token qua bottom sheet chọn tài khoản. Ném `GetCredentialCancellationException` khi người dùng
 * hủy, `NoCredentialException` khi máy không có tài khoản Google (`AuthErrorMapper` đổi thành `AuthError`).
 */
interface GoogleCredentialDataSource {
    suspend fun requestIdToken(activity: Activity): String
}

/** Credential Manager theo research R12. `default_web_client_id` do plugin google-services sinh. */
class CredentialManagerGoogleDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : GoogleCredentialDataSource {

    override suspend fun requestIdToken(activity: Activity): String {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Credential không phải Google ID token: ${credential.type}"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

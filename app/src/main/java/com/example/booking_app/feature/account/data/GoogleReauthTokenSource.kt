package com.example.booking_app.feature.account.data

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
 * Lấy Google ID token để xác nhận lại danh tính (research R8). Khác màn hình đăng nhập của 010: chỉ hiện tài khoản
 * đã cấp quyền cho app (`setFilterByAuthorizedAccounts(true)`). Hủy thì ném `GetCredentialCancellationException`,
 * không có tài khoản thì `NoCredentialException`. [activity] chỉ dùng trong lời gọi, không giữ lại.
 */
interface GoogleReauthTokenSource {
    suspend fun requestIdToken(activity: Activity): String
}

class CredentialManagerGoogleReauthTokenSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : GoogleReauthTokenSource {

    override suspend fun requestIdToken(activity: Activity): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Credential không phải Google ID token: ${credential.type}"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

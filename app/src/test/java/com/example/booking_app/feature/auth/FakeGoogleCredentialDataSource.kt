package com.example.booking_app.feature.auth

import android.app.Activity
import com.example.booking_app.feature.auth.data.GoogleCredentialDataSource

class FakeGoogleCredentialDataSource : GoogleCredentialDataSource {
    var result: () -> String = { "google-id-token" }
    var calls = 0

    override suspend fun requestIdToken(activity: Activity): String {
        calls++
        return result()
    }
}

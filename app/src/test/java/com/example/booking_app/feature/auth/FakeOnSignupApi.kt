package com.example.booking_app.feature.auth

import com.example.booking_app.feature.auth.data.OnSignupApi
import com.example.booking_app.feature.auth.data.OnSignupRequest
import com.example.booking_app.feature.auth.data.OnSignupResponse
import kotlinx.coroutines.CompletableDeferred

class FakeOnSignupApi : OnSignupApi {
    var result: () -> OnSignupResponse = { OnSignupResponse("CREATED", "PLAYER") }
    var gate: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<OnSignupRequest>()

    override suspend fun onSignup(request: OnSignupRequest): OnSignupResponse {
        requests += request
        gate?.await()
        return result()
    }
}

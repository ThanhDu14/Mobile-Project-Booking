package com.example.booking_app.feature.account

import com.example.booking_app.feature.account.data.AccountFunctionsApi

/** Ghi lại lời gọi tới các Edge Function của 011; [syncResult] quyết định kết quả `sync-account-email`. */
open class FakeAccountFunctionsApi : AccountFunctionsApi {
    val calls = mutableListOf<String>()
    var syncResult: suspend () -> Boolean = { true }

    override suspend fun syncAccountEmail(): Boolean {
        calls += "sync"
        return syncResult()
    }
}

package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.feature.account.data.AccountFunctionsApi
import javax.inject.Inject

/** Nhờ server chép email đã xác minh trong token vào `users.email` (US7, contracts §3). `true` khi có ghi. */
class SyncAccountEmailUseCase @Inject constructor(
    private val api: AccountFunctionsApi,
) {
    suspend operator fun invoke(): Boolean = api.syncAccountEmail()
}

package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.feature.account.data.AccountFunctionsApi
import com.example.booking_app.feature.account.domain.model.DeletionImpact
import javax.inject.Inject

/** Bản xem trước tác động của việc xóa tài khoản (FR-022, `delete-account` action `preview`). */
class LoadDeletionImpactUseCase @Inject constructor(
    private val api: AccountFunctionsApi,
) {
    suspend operator fun invoke(): DeletionImpact = api.previewDeletion()
}

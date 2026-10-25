package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.feature.account.domain.AccountRepository
import com.example.booking_app.feature.account.domain.model.ProfileDraft
import javax.inject.Inject

/** Lưu họ tên, trình độ, khu vực. ViewModel kiểm tra dữ liệu nhập trước (NameRule, DistrictSelectionRule). */
class SaveProfileUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(saved: ProfileDraft, draft: ProfileDraft): Boolean =
        accountRepository.updateProfile(saved, draft)
}

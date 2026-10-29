package com.example.booking_app.feature.owner.domain.usecase

import com.example.booking_app.feature.owner.domain.OwnerAccountState
import com.example.booking_app.feature.owner.domain.OwnerOnboardingRepository
import javax.inject.Inject

class GetOwnerAccountStateUseCase @Inject constructor(
    private val repository: OwnerOnboardingRepository,
) {
    suspend operator fun invoke(): OwnerAccountState = repository.getAccountState()
}

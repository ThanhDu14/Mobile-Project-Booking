package com.example.booking_app.feature.owner.domain.usecase

import com.example.booking_app.feature.owner.domain.OwnerApplication
import com.example.booking_app.feature.owner.domain.OwnerOnboardingRepository
import javax.inject.Inject

class GetApplicantApplicationsUseCase @Inject constructor(
    private val repository: OwnerOnboardingRepository,
) {
    suspend operator fun invoke(): List<OwnerApplication> = repository.getApplicantApplications()
}

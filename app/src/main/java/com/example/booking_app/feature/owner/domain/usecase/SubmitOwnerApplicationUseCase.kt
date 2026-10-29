package com.example.booking_app.feature.owner.domain.usecase

import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmitCommand
import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmitResult
import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmissionRepository
import javax.inject.Inject

class SubmitOwnerApplicationUseCase @Inject constructor(
    private val repository: OwnerApplicationSubmissionRepository,
) {
    suspend operator fun invoke(command: OwnerApplicationSubmitCommand): OwnerApplicationSubmitResult =
        repository.submit(command)
}

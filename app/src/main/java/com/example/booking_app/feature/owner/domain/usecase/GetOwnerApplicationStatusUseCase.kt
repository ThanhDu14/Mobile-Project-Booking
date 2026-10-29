package com.example.booking_app.feature.owner.domain.usecase

import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMapping
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMapper
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusRepository
import javax.inject.Inject

class GetOwnerApplicationStatusUseCase @Inject constructor(
    private val repository: OwnerApplicationStatusRepository,
) {
    suspend operator fun invoke(): OwnerApplicationStatusMapping =
        OwnerApplicationStatusMapper.map(repository.getAuthoritativeStatus())
}

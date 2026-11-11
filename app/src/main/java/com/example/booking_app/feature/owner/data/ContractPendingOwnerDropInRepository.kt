package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInContractUnavailableException
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInDraft
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRegistration
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRepository
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInSession
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Fails closed pending the authoritative 080/081 owner-safe roster/session contract. */
class ContractPendingOwnerDropInRepository @Inject constructor() : OwnerDropInRepository {
    override fun observeSessions(venueId: String): Flow<List<OwnerDropInSession>> = flow {
        throw OwnerDropInContractUnavailableException()
    }

    override suspend fun getRoster(venueId: String, sessionId: String): Result<List<OwnerDropInRegistration>> =
        Result.failure(OwnerDropInContractUnavailableException())

    override suspend fun createSession(draft: OwnerDropInDraft): Result<String> =
        Result.failure(OwnerDropInContractUnavailableException())

    override suspend fun updateSession(venueId: String, sessionId: String, draft: OwnerDropInDraft): Result<Unit> =
        Result.failure(OwnerDropInContractUnavailableException())

    override suspend fun cancelSession(venueId: String, sessionId: String, reason: String): Result<Unit> =
        Result.failure(OwnerDropInContractUnavailableException())

    override suspend fun checkIn(venueId: String, sessionId: String, credential: String): Result<Unit> =
        Result.failure(OwnerDropInContractUnavailableException())
}

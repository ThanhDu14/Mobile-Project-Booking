package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.network.parseFunctionErrorCode
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusRepository
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusSnapshot
import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmitCommand
import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmitResult
import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmissionRepository
import com.example.booking_app.feature.owner.domain.OwnerStatus
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class OwnerApplicationSubmissionRepositoryImpl @Inject constructor(
    private val api: OwnerApplicationSubmitApi,
    private val statusRepository: OwnerApplicationStatusRepository,
) : OwnerApplicationSubmissionRepository {
    override suspend fun submit(command: OwnerApplicationSubmitCommand): OwnerApplicationSubmitResult {
        val first = try {
            api.submit(command.applicationId, command.draft)
        } catch (e: AppError.Timeout) {
            return resolveTimeout(command)
        } catch (e: AppError.Unknown) {
            when (parseFunctionErrorCode((e.cause as? FunctionHttpException)?.body)) {
                "CONFLICT", "CONFLICT_STALE" -> return OwnerApplicationSubmitResult.Conflict
                "AMBIGUOUS_RESULT" -> return OwnerApplicationSubmitResult.Ambiguous
                else -> throw e
            }
        }
        return confirmReadBack(command.applicationId, first)
    }

    private suspend fun resolveTimeout(command: OwnerApplicationSubmitCommand): OwnerApplicationSubmitResult {
        val firstRead = readBackOrNull() ?: return OwnerApplicationSubmitResult.Ambiguous
        outcomeFor(firstRead, command.applicationId)?.let { return it }
        if (firstRead.ownerStatus != OwnerStatus.NONE) return OwnerApplicationSubmitResult.Ambiguous

        val retried = try {
            api.submit(command.applicationId, command.draft)
        } catch (e: AppError.Timeout) {
            return readBackOrNull()?.let { outcomeFor(it, command.applicationId) }
                ?: OwnerApplicationSubmitResult.Ambiguous
        } catch (e: AppError.Unknown) {
            when (parseFunctionErrorCode((e.cause as? FunctionHttpException)?.body)) {
                "CONFLICT", "CONFLICT_STALE" -> return OwnerApplicationSubmitResult.Conflict
                "AMBIGUOUS_RESULT" -> return OwnerApplicationSubmitResult.Ambiguous
                else -> throw e
            }
        }
        return confirmReadBack(command.applicationId, retried)
    }

    private suspend fun confirmReadBack(expectedId: String, returnedId: String): OwnerApplicationSubmitResult {
        if (returnedId != expectedId) return OwnerApplicationSubmitResult.Conflict
        val snapshot = readBackOrNull() ?: return OwnerApplicationSubmitResult.Ambiguous
        return outcomeFor(snapshot, expectedId) ?: OwnerApplicationSubmitResult.Ambiguous
    }

    private suspend fun readBackOrNull(): OwnerApplicationStatusSnapshot? = try {
        statusRepository.getAuthoritativeStatus()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun outcomeFor(
        snapshot: OwnerApplicationStatusSnapshot,
        expectedId: String,
    ): OwnerApplicationSubmitResult? {
        val application = snapshot.application
        if (application?.id == expectedId) {
            return when (snapshot.ownerStatus) {
                OwnerStatus.PENDING -> OwnerApplicationSubmitResult.Submitted(expectedId)
                OwnerStatus.APPROVED, OwnerStatus.REJECTED -> OwnerApplicationSubmitResult.Conflict
                OwnerStatus.NONE, null -> null
            }
        }
        if (snapshot.ownerStatus == OwnerStatus.PENDING && application?.id != expectedId) {
            return OwnerApplicationSubmitResult.Conflict
        }
        if (snapshot.ownerStatus == OwnerStatus.NONE) return null
        return OwnerApplicationSubmitResult.Ambiguous
    }
}

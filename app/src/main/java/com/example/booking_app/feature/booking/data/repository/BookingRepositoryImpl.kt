package com.example.booking_app.feature.booking.data.repository

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.booking.data.datasource.BookingRemoteDataSource
import com.example.booking_app.feature.booking.data.mapper.BookingSlotMapper
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

class BookingRepositoryImpl @Inject constructor(
    private val remoteDataSource: BookingRemoteDataSource
) : BookingRepository {

    override suspend fun getVenueOperatingHours(venueId: String): Result<Pair<String, String>> {
        return safeApiCall {
            remoteDataSource.getVenueOperatingHours(venueId)
        }
    }

    override suspend fun getActiveCourts(venueId: String): Result<List<Court>> {
        return safeApiCall {
            remoteDataSource.getActiveCourts(venueId).map { BookingSlotMapper.toDomain(it) }
        }
    }

    override suspend fun getPriceRules(venueId: String): Result<List<PriceRule>> {
        return safeApiCall {
            remoteDataSource.getPriceRules(venueId).map { BookingSlotMapper.toDomain(it) }
        }
    }

    override fun getBusySlotsRealtime(venueId: String, date: String): Flow<List<TimeSlot>> {
        return remoteDataSource.getBusySlotsRealtime(venueId, date)
            .map { dtos ->
                dtos.map { BookingSlotMapper.toDomain(it) }
            }
            .catch { e ->
                throw mapToAppError(e)
            }
    }

    override suspend fun holdSlotsAtomic(
        venueId: String,
        courtId: String,
        date: String,
        slotIds: List<String>,
        userId: String,
        requestId: String
    ): Result<HoldResult> {
        return safeApiCall {
            remoteDataSource.holdSlotsAtomic(
                venueId = venueId,
                courtId = courtId,
                date = date,
                slotIds = slotIds,
                userId = userId,
                requestId = requestId
            )
        }
    }

    override suspend fun releaseHoldSlots(
        venueId: String,
        slotIds: List<String>,
        userId: String
    ): Result<Unit> {
        return safeApiCall {
            remoteDataSource.releaseHoldSlots(
                venueId = venueId,
                slotIds = slotIds,
                userId = userId
            )
        }
    }

    private inline fun <T> safeApiCall(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(mapToAppError(e))
        }
    }

    private fun mapToAppError(throwable: Throwable): AppError {
        return when (throwable) {
            is AppError -> throwable
            is FirebaseFirestoreException -> {
                when (throwable.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.Network
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Timeout
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AppError.RateLimited
                    FirebaseFirestoreException.Code.ABORTED -> AppError.Conflict
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.NotSignedIn
                    else -> AppError.Unknown(throwable)
                }
            }
            is IOException -> AppError.Network
            is TimeoutException -> AppError.Timeout
            else -> AppError.Unknown(throwable)
        }
    }
}

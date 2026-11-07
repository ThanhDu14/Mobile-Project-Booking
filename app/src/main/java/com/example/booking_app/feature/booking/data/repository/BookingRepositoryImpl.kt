package com.example.booking_app.feature.booking.data.repository

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.util.SystemAppClock
import com.example.booking_app.feature.booking.data.cache.VenueMetadataCache
import com.example.booking_app.feature.booking.data.datasource.BookingRemoteDataSource
import com.example.booking_app.feature.booking.data.mapper.BookingSlotMapper
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.model.VenueInfo
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

class BookingRepositoryImpl @Inject constructor(
    private val remoteDataSource: BookingRemoteDataSource,
    private val venueMetadataCache: VenueMetadataCache
) : BookingRepository {

    override suspend fun getVenueOperatingHours(venueId: String): Result<Pair<String, String>> {
        return safeApiCall {
            remoteDataSource.getVenueOperatingHours(venueId)
        }
    }

    override suspend fun getVenueInfo(venueId: String, forceRefresh: Boolean): Result<VenueInfo> {
        val cached = venueMetadataCache.getVenueInfo(venueId, forceRefresh)
        if (cached != null) {
            return Result.success(cached)
        }
        return safeApiCall {
            val remoteInfo = remoteDataSource.getVenueInfo(venueId)
            venueMetadataCache.putVenueInfo(venueId, remoteInfo)
            remoteInfo
        }
    }

    override suspend fun getActiveCourts(venueId: String, forceRefresh: Boolean): Result<List<Court>> {
        val cached = venueMetadataCache.getCourts(venueId, forceRefresh)
        if (cached != null) {
            return Result.success(cached)
        }
        return safeApiCall {
            val courts = remoteDataSource.getActiveCourts(venueId).map { BookingSlotMapper.toDomain(it) }
            venueMetadataCache.putCourts(venueId, courts)
            courts
        }
    }

    override suspend fun getPriceRules(venueId: String, forceRefresh: Boolean): Result<List<PriceRule>> {
        val cached = venueMetadataCache.getPriceRules(venueId, forceRefresh)
        if (cached != null) {
            return Result.success(cached)
        }
        return safeApiCall {
            val rules = remoteDataSource.getPriceRules(venueId).map { BookingSlotMapper.toDomain(it) }
            venueMetadataCache.putPriceRules(venueId, rules)
            rules
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
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.NotSignedIn
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        val isSignedIn = runCatching {
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
                        }.getOrDefault(false)
                        if (!isSignedIn) {
                            AppError.NotSignedIn
                        } else {
                            AppError.Unknown(SecurityException("Quyền truy cập bị từ chối hoặc vi phạm quy tắc: ${throwable.message}", throwable))
                        }
                    }
                    else -> AppError.Unknown(throwable)
                }
            }
            is IOException -> AppError.Network
            is TimeoutException -> AppError.Timeout
            else -> AppError.Unknown(throwable)
        }
    }
}

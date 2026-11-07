package com.example.booking_app.feature.booking.data.repository

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.util.SystemAppClock
import com.example.booking_app.feature.booking.data.cache.VenueMetadataCache
import com.example.booking_app.feature.booking.data.datasource.RecurringBookingRemoteDataSource
import com.example.booking_app.feature.booking.data.mapper.BookingSlotMapper
import com.example.booking_app.feature.booking.data.mapper.RecurringBookingMapper
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.model.RecurringSummary
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeoutException
import javax.inject.Inject

class RecurringBookingRepositoryImpl @Inject constructor(
    private val remoteDataSource: RecurringBookingRemoteDataSource,
    private val venueMetadataCache: VenueMetadataCache,
    private val serverTimeOffsetProvider: ServerTimeOffsetProvider
) : RecurringBookingRepository {

    override suspend fun isRecurringAccepted(venueId: String, forceRefresh: Boolean): Result<Boolean> {
        val cachedVenue = venueMetadataCache.getVenueInfo(venueId, forceRefresh)
        if (cachedVenue != null) {
            return Result.success(cachedVenue.acceptsRecurring)
        }
        return safeApiCall {
            remoteDataSource.isRecurringAccepted(venueId)
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

    override suspend fun getVenueName(venueId: String, forceRefresh: Boolean): Result<String> {
        val cached = venueMetadataCache.getVenueInfo(venueId, forceRefresh)
        if (cached != null) {
            return Result.success(cached.name)
        }
        return Result.success("")
    }

    override suspend fun getBusySlotsForDates(
        venueId: String,
        dates: List<String>
    ): Result<List<TimeSlot>> {
        return safeApiCall {
            remoteDataSource.getBusySlotsForDates(venueId, dates).map { BookingSlotMapper.toDomain(it) }
        }
    }

    override suspend fun holdRecurringBookingTransaction(
        venueId: String,
        config: RecurringConfig,
        sessions: List<RecurringSession>,
        userId: String,
        requestId: String
    ): Result<RecurringHoldResult> {
        serverTimeOffsetProvider.ensureSynced()
        return safeApiCall {
            val validSessions = sessions.filter { !it.isExcluded && it.status != RecurringSessionStatus.MUST_SKIP }
            val subtotal = validSessions.sumOf { it.price }
            val rawDeposit = (subtotal * 30L) / 100L
            val deposit = ((rawDeposit + 500L) / 1000L) * 1000L

            val recurringGroupId = requestId
            val holdExpiresAtMillis = serverTimeOffsetProvider.now() + 5 * 60 * 1000L
            val venueName = venueMetadataCache.getVenueInfo(venueId)?.name.orEmpty()

            val summary = RecurringSummary(
                totalSessions = sessions.size,
                validSessions = validSessions,
                subtotalAmount = subtotal,
                depositAmount = deposit,
                canProceed = validSessions.size >= 4
            )

            val groupDto = RecurringBookingMapper.toRecurringGroupDto(
                recurringGroupId = recurringGroupId,
                config = config,
                venueName = venueName,
                sessions = sessions,
                summary = summary,
                userId = userId,
                requestId = requestId,
                holdExpiresAtMillis = holdExpiresAtMillis
            )

            val sessionDtos = validSessions.map { session ->
                val dateStr = session.date.format(RecurringBookingMapper.DATE_FORMATTER)
                val deterministicBookingId = "$requestId-$dateStr-${session.startTime}"
                RecurringBookingMapper.toBookingSessionDto(
                    session = session,
                    recurringGroupId = recurringGroupId,
                    userId = userId,
                    venueId = venueId,
                    bookingId = deterministicBookingId
                )
            }

            val allSlotIds = validSessions.flatMap { it.slotIds }

            remoteDataSource.holdRecurringBookingTransaction(
                venueId = venueId,
                recurringGroup = groupDto,
                sessions = sessionDtos,
                slotIds = allSlotIds,
                userId = userId
            )
        }
    }

    override suspend fun releaseRecurringHold(
        recurringGroupId: String,
        slotIds: List<String>,
        userId: String
    ): Result<Unit> {
        return safeApiCall {
            remoteDataSource.releaseRecurringHold(
                recurringGroupId = recurringGroupId,
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

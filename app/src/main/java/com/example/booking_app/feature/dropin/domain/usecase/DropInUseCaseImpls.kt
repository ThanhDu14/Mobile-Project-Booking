package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.booking.domain.UpcomingBookingsReader
import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.DropInSessionRepository
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.DropInWarning
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/** Chờ tối đa lần phát đầu của các nguồn phụ (hồ sơ, lượt sắp tới), để màn hình không treo khi offline. */
internal const val FIRST_VALUE_TIMEOUT_MS = 3_000L

/** T023. */
class LoadDropInPageImpl @Inject constructor(
    private val repository: DropInSessionRepository,
) : LoadDropInPage {
    override suspend fun invoke(filter: DropInFilter, cursor: DropInCursor?): Result<Page<DropInSession>> {
        if (filter.validate() != null) return Result.failure(DropInError.InvalidArgument())
        return repository.page(filter, cursor)
    }
}

/** T023: điền sẵn khu vực yêu thích (tối đa [DropInFilter.MAX_DISTRICTS]); khách hoặc lỗi → bộ lọc rỗng. */
class GetDefaultDropInFilterImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val profileReader: UserProfileReader,
) : GetDefaultDropInFilter {
    override suspend fun invoke(): DropInFilter {
        if (sessionManager.currentUid() == null) return DropInFilter()
        val profile = readProfile(profileReader) ?: return DropInFilter()
        val districts = profile.favoriteDistrictIds.filter { it.isNotBlank() }.distinct().take(DropInFilter.MAX_DISTRICTS)
        return DropInFilter(districtIds = districts.toSet())
    }
}

/** T023. */
class ObserveDropInSessionImpl @Inject constructor(
    private val repository: DropInSessionRepository,
) : ObserveDropInSession {
    override fun invoke(sessionId: String): Flow<DropInSession?> = repository.observe(sessionId)
}

class ObserveMyDropInRegistrationImpl @Inject constructor(
    private val repository: DropInRegistrationRepository,
) : ObserveMyDropInRegistration {
    override fun invoke(sessionId: String): Flow<DropInRegistration?> = repository.observeMine(sessionId)
}

/** T036: kiểm tra nhanh ở app (server vẫn kiểm tra lại), không tự thử lại. */
class RegisterDropInImpl @Inject constructor(
    private val repository: DropInRegistrationRepository,
) : RegisterDropIn {
    override suspend fun invoke(req: RegisterRequest): Result<DropInRegistration> {
        if (req.partySize !in MIN_PARTY..MAX_PARTY || req.requestId.isBlank() || req.sessionId.isBlank()) {
            return Result.failure(DropInError.InvalidArgument())
        }
        return repository.register(req)
    }

    companion object {
        const val MIN_PARTY = 1
        const val MAX_PARTY = 4
    }
}

/** FR-008a: kiểm tra nhanh ở app (1–3 người, có sessionId/requestId); server kiểm tra tổng ≤ 4 và chỗ còn. */
class AddDropInPartyImpl @Inject constructor(
    private val repository: DropInRegistrationRepository,
) : AddDropInParty {
    override suspend fun invoke(sessionId: String, additional: Int, requestId: String): Result<DropInRegistration> {
        if (additional !in 1 until RegisterDropInImpl.MAX_PARTY || sessionId.isBlank() || requestId.isBlank()) {
            return Result.failure(DropInError.InvalidArgument())
        }
        return repository.addParty(sessionId, additional, requestId)
    }
}

/**
 * T036 (FR-012, research R11): chỉ cảnh báo, không chặn. Mỗi nguồn lỗi hoặc quá hạn thì bỏ cảnh báo
 * của nguồn đó. Khách → rỗng.
 */
class CheckDropInWarningsImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val registrations: DropInRegistrationRepository,
    private val bookings: UpcomingBookingsReader,
    private val profileReader: UserProfileReader,
) : CheckDropInWarnings {

    override suspend fun invoke(session: DropInSession): List<DropInWarning> {
        val uid = sessionManager.currentUid() ?: return emptyList()
        return buildList {
            if (overlapsDropIn(uid, session)) add(DropInWarning.OVERLAP_DROP_IN)
            if (overlapsBooking(uid, session)) add(DropInWarning.OVERLAP_BOOKING)
            if (levelMismatch(session)) add(DropInWarning.LEVEL_MISMATCH)
        }
    }

    private suspend fun overlapsDropIn(uid: String, session: DropInSession): Boolean = safely {
        val upcoming = withTimeoutOrNull(FIRST_VALUE_TIMEOUT_MS) {
            registrations.observeUpcoming(uid).firstOrNull()
        }.orEmpty()
        upcoming.any { reg ->
            val start = reg.sessionStartAt
            val end = reg.sessionEndAt
            reg.sessionId != session.id && reg.isActive && start != null && end != null &&
                overlaps(start, end, session.startAt, session.endAt)
        }
    }

    private suspend fun overlapsBooking(uid: String, session: DropInSession): Boolean = safely {
        bookings.upcoming(uid, session.startAt, session.endAt)
            .any { overlaps(it.startAt, it.endAt, session.startAt, session.endAt) }
    }

    private suspend fun levelMismatch(session: DropInSession): Boolean = safely {
        val sessionLevel = session.level ?: return@safely false
        val myLevel = readProfile(profileReader)?.level?.toLevelOrNull() ?: return@safely false
        myLevel != sessionLevel
    }

    private inline fun safely(block: () -> Boolean): Boolean = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}

/** Hai khoảng nửa mở `[aStart, aEnd)` và `[bStart, bEnd)` giao nhau; chạm biên không tính là trùng. */
internal fun overlaps(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Boolean =
    aStart < bEnd && bStart < aEnd

private suspend fun readProfile(reader: UserProfileReader): MyProfile? = try {
    withTimeoutOrNull(FIRST_VALUE_TIMEOUT_MS) { reader.observeMyProfile().firstOrNull() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

private fun String.toLevelOrNull(): Level? =
    Level.entries.firstOrNull { it.name == trim().uppercase(Locale.ROOT) }

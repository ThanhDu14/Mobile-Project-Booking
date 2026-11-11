package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.DropInSessionLookup
import com.example.booking_app.feature.dropin.domain.RegistrationOutcomeResolver
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.MyDropInItem
import com.example.booking_app.feature.dropin.domain.model.Page
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant
import javax.inject.Inject

/** T046: dữ liệu cho "Lượt vãng lai của tôi" (US3, research R7) của người đang đăng nhập. */
interface LoadMyDropIns {
    /** Tab "Sắp tới", realtime, giờ gần nhất trước. Khách → danh sách rỗng. */
    fun upcoming(): Flow<List<MyDropInItem>>

    /** Tab "Đã qua", mới nhất trước, mỗi trang 20 lượt. Khách → trang rỗng. */
    suspend fun past(cursor: DropInCursor?): Result<Page<MyDropInItem>>
}

/** Ghép đăng ký với buổi (đọc theo lô) và nhãn kết quả từ [RegistrationOutcomeResolver]. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadMyDropInsImpl @Inject constructor(
    private val repository: DropInRegistrationRepository,
    private val sessionLookup: DropInSessionLookup,
    private val sessionManager: SessionManager,
    private val clock: AppClock,
) : LoadMyDropIns {

    override fun upcoming(): Flow<List<MyDropInItem>> {
        val uid = sessionManager.currentUid() ?: return flowOf(emptyList())
        return repository.observeUpcoming(uid).mapLatest { registrations ->
            toItems(registrations).sortedBy { it.startAt ?: Instant.MAX }
        }
    }

    override suspend fun past(cursor: DropInCursor?): Result<Page<MyDropInItem>> {
        val uid = sessionManager.currentUid() ?: return Result.success(Page(emptyList(), null))
        val page = repository.pagePast(uid, cursor).getOrElse { return Result.failure(it) }
        val items = toItems(page.items).sortedByDescending { it.startAt ?: Instant.MIN }
        return Result.success(Page(items, page.next))
    }

    private suspend fun toItems(registrations: List<DropInRegistration>): List<MyDropInItem> {
        if (registrations.isEmpty()) return emptyList()
        val sessions = sessionLookup.getByIds(registrations.map { it.sessionId })
        val now = Instant.ofEpochMilli(clock.now())
        return registrations.map { registration ->
            val session = sessions[registration.sessionId]
            MyDropInItem(
                registration = registration,
                session = session,
                outcome = RegistrationOutcomeResolver.resolve(registration, session, now),
            )
        }
    }
}

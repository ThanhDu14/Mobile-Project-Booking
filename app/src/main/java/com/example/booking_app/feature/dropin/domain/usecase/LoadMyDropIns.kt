package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.DropInSessionLookup
import com.example.booking_app.feature.dropin.domain.DropInSessionRepository
import com.example.booking_app.feature.dropin.domain.RegistrationOutcomeResolver
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.MyDropInItem
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val sessionRepository: DropInSessionRepository,
    private val sessionManager: SessionManager,
    private val clock: AppClock,
) : LoadMyDropIns {

    override fun upcoming(): Flow<List<MyDropInItem>> {
        val uid = sessionManager.currentUid() ?: return flowOf(emptyList())
        return repository.observeUpcoming(uid).flatMapLatest { registrations ->
            // Lượt WAITLISTED: nghe document buổi để vị trí đổi khi người phía trước rời hàng chờ.
            val waitingIds = registrations.filter { it.status == RegistrationStatus.WAITLISTED }
                .map { it.sessionId }.distinct().take(MAX_LIVE_SESSIONS)
            if (waitingIds.isEmpty()) {
                flow { emit(toItems(registrations, emptyMap()).sortedBy { it.startAt ?: Instant.MAX }) }
            } else {
                combine(waitingIds.map { id -> sessionRepository.observe(id).catch { emit(null) } }) { live ->
                    waitingIds.zip(live.toList()).mapNotNull { (id, s) -> s?.let { id to it } }.toMap()
                }.flatMapLatest { liveSessions ->
                    flow { emit(toItems(registrations, liveSessions).sortedBy { it.startAt ?: Instant.MAX }) }
                }
            }
        }
    }

    override suspend fun past(cursor: DropInCursor?): Result<Page<MyDropInItem>> {
        val uid = sessionManager.currentUid() ?: return Result.success(Page(emptyList(), null))
        val page = repository.pagePast(uid, cursor).getOrElse { return Result.failure(it) }
        val items = toItems(page.items).sortedByDescending { it.startAt ?: Instant.MIN }
        return Result.success(Page(items, page.next))
    }

    /** [live] (buổi đang nghe realtime) ghi đè kết quả đọc theo lô. */
    private suspend fun toItems(
        registrations: List<DropInRegistration>,
        live: Map<String, DropInSession> = emptyMap(),
    ): List<MyDropInItem> {
        if (registrations.isEmpty()) return emptyList()
        val sessions = sessionLookup.getByIds(registrations.map { it.sessionId }.filter { it !in live }) + live
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

    private companion object {
        /** Giới hạn listener realtime; lượt chờ vượt mức này vẫn hiện bằng dữ liệu đọc một lần. */
        const val MAX_LIVE_SESSIONS = 10
    }
}

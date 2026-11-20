package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.usecase.LoadMyDropInsImpl
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Vị trí chờ trong "Lượt vãng lai của tôi" phải đổi theo document buổi (spec 081, lỗi thủ công 2026-11-19). */
class LoadMyDropInsTest {

    private val registrations = MutableStateFlow(emptyList<DropInRegistration>())
    private val liveSessions = mutableMapOf<String, MutableStateFlow<DropInSession?>>()
    private var observed = 0

    private val registrationRepo = mockk<DropInRegistrationRepository> {
        every { observeUpcoming("u1") } returns registrations
    }
    private val sessionRepo = object : DropInSessionRepository {
        override suspend fun page(filter: DropInFilter, cursor: DropInCursor?): Result<Page<DropInSession>> =
            Result.success(Page(emptyList(), null))

        override fun observe(sessionId: String): Flow<DropInSession?> {
            observed++
            return liveSessions.getValue(sessionId)
        }
    }
    private val lookup = object : DropInSessionLookup {
        override suspend fun getByIds(ids: Collection<String>) =
            ids.associateWith { session(id = it, waitlistTickets = listOf(1, 2, 3)) }
    }
    private val sessionManager = mockk<SessionManager> { every { currentUid() } returns "u1" }
    private val clock = object : AppClock {
        override fun now(): Long = NOW.toEpochMilli()
        override fun elapsedRealtime(): Long = 0
    }

    private val useCase = LoadMyDropInsImpl(registrationRepo, lookup, sessionRepo, sessionManager, clock)

    @Test
    fun `luot WAITLISTED cap nhat khi document buoi doi`() = runTest {
        liveSessions["w"] = MutableStateFlow(session(id = "w", waitlistTickets = listOf(1, 2, 3)))
        registrations.value = listOf(
            registration(sessionId = "w", status = RegistrationStatus.WAITLISTED, waitlistTicket = 3),
        )
        val flow = useCase.upcoming()
        assertEquals(listOf(1, 2, 3), flow.first().single().session?.waitlistTickets)

        liveSessions.getValue("w").value = session(id = "w", waitlistTickets = listOf(2, 3))
        assertEquals(listOf(2, 3), flow.first().single().session?.waitlistTickets)
    }

    @Test
    fun `luot REGISTERED khong nghe buoi realtime`() = runTest {
        registrations.value = listOf(registration(sessionId = "r", status = RegistrationStatus.REGISTERED))

        assertEquals("r", useCase.upcoming().first().single().sessionId)
        assertEquals(0, observed)
    }
}

package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.booking.domain.UpcomingBooking
import com.example.booking_app.feature.booking.domain.UpcomingBookingsReader
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.DropInWarning
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import com.example.booking_app.feature.dropin.domain.usecase.CheckDropInWarningsImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CheckDropInWarningsTest {

    private val start = Instant.parse("2026-11-12T11:00:00Z") // 18:00–20:00 +07
    private val end = Instant.parse("2026-11-12T13:00:00Z")

    private val session = DropInSession(
        id = "s1", venueId = "v1", ownerId = "o1", districtId = "q1", venueName = "A", venueAddress = "",
        districtName = "", courtIds = listOf("c1"), startAt = start, endAt = end, timeSlot = TimeSlot.EVENING,
        level = Level.INTERMEDIATE, capacity = 12, registeredCount = 3, pricePerPerson = 60_000,
        status = SessionStatus.OPEN, note = null,
    )

    private fun reg(sessionId: String, s: Instant, e: Instant, status: RegistrationStatus = RegistrationStatus.REGISTERED) =
        DropInRegistration(
            sessionId = sessionId, userId = "u1", partySize = 1, status = status,
            paymentMethod = PaymentMethod.AT_VENUE, paymentStatus = PaymentStatus.UNPAID, pricePerPerson = 0,
            amount = 0, refundAmount = null, qrToken = null, ticketCode = null, sessionChanged = false,
            sessionStartAt = s, sessionEndAt = e, registeredAt = null, cancelledBy = null,
        )

    private class FakeBookings(var items: List<UpcomingBooking> = emptyList(), var fail: Boolean = false) :
        UpcomingBookingsReader {
        override suspend fun upcoming(uid: String, from: Instant, to: Instant): List<UpcomingBooking> {
            if (fail) error("boom")
            return items
        }
    }

    private class FakeProfiles(var flow: Flow<MyProfile?>) : UserProfileReader {
        override fun observeMyProfile(): Flow<MyProfile?> = flow
    }

    private fun profile(level: String?) = MyProfile(
        displayName = "P", level = level, favoriteDistrictIds = emptyList(), avatarPath = null, role = "PLAYER",
        accountStatus = "ACTIVE",
    )

    private val sessionManager = mockk<SessionManager> { every { currentUid() } returns "u1" }
    private val registrations = mockk<DropInRegistrationRepository> { every { observeUpcoming(any()) } returns flowOf(emptyList()) }
    private val bookings = FakeBookings()
    private val profiles = FakeProfiles(flowOf(profile("INTERMEDIATE")))
    private val check = CheckDropInWarningsImpl(sessionManager, registrations, bookings, profiles)

    @Test
    fun `khong trung, cung trinh do - khong canh bao`() = runTest {
        assertEquals(emptyList<DropInWarning>(), check(session))
    }

    @Test
    fun `trung gio voi luot vang lai khac`() = runTest {
        every { registrations.observeUpcoming("u1") } returns flowOf(
            listOf(reg("s2", start.plusSeconds(3600), end.plusSeconds(3600))),
        )
        assertEquals(listOf(DropInWarning.OVERLAP_DROP_IN), check(session))
    }

    @Test
    fun `luot vang lai cham bien, cua chinh buoi nay hoac da huy - khong tinh`() = runTest {
        every { registrations.observeUpcoming("u1") } returns flowOf(
            listOf(
                reg("s2", end, end.plusSeconds(3600)),
                reg("s3", start.minusSeconds(3600), start),
                reg("s1", start, end),
                reg("s4", start, end, RegistrationStatus.CANCELLED),
            ),
        )
        assertEquals(emptyList<DropInWarning>(), check(session))
    }

    @Test
    fun `trung gio voi don dat san`() = runTest {
        bookings.items = listOf(UpcomingBooking("b1", "v9", start.minusSeconds(1800), start.plusSeconds(1800)))
        assertEquals(listOf(DropInWarning.OVERLAP_BOOKING), check(session))
    }

    @Test
    fun `don dat san khong giao - khong canh bao`() = runTest {
        bookings.items = listOf(UpcomingBooking("b1", "v9", end, end.plusSeconds(3600)))
        assertEquals(emptyList<DropInWarning>(), check(session))
    }

    @Test
    fun `lech trinh do`() = runTest {
        profiles.flow = flowOf(profile("BEGINNER"))
        assertEquals(listOf(DropInWarning.LEVEL_MISMATCH), check(session))
    }

    @Test
    fun `buoi moi trinh do hoac ho so chua chon trinh do - khong canh bao trinh do`() = runTest {
        assertEquals(emptyList<DropInWarning>(), check(session.copy(level = null)))
        profiles.flow = flowOf(profile(null))
        assertEquals(emptyList<DropInWarning>(), check(session))
        profiles.flow = flowOf(profile("PRO"))
        assertEquals(emptyList<DropInWarning>(), check(session))
    }

    @Test
    fun `du ca ba canh bao, theo thu tu`() = runTest {
        every { registrations.observeUpcoming("u1") } returns flowOf(listOf(reg("s2", start, end)))
        bookings.items = listOf(UpcomingBooking("b1", null, start, end))
        profiles.flow = flowOf(profile("ADVANCED"))
        assertEquals(
            listOf(DropInWarning.OVERLAP_DROP_IN, DropInWarning.OVERLAP_BOOKING, DropInWarning.LEVEL_MISMATCH),
            check(session),
        )
    }

    @Test
    fun `nguon loi hoac rong - bo canh bao cua nguon do, khong nem loi`() = runTest {
        every { registrations.observeUpcoming("u1") } returns flow { error("offline") }
        bookings.fail = true
        profiles.flow = flowOf(profile("BEGINNER"))
        assertEquals(listOf(DropInWarning.LEVEL_MISMATCH), check(session))

        every { registrations.observeUpcoming("u1") } returns emptyFlow()
        profiles.flow = flow { error("offline") }
        assertEquals(emptyList<DropInWarning>(), check(session))
    }

    @Test
    fun `nguon khong phat gi - het thoi gian cho thi bo qua`() = runTest {
        every { registrations.observeUpcoming("u1") } returns flow { kotlinx.coroutines.awaitCancellation() }
        profiles.flow = flow { kotlinx.coroutines.awaitCancellation() }
        assertEquals(emptyList<DropInWarning>(), check(session))
    }

    @Test
    fun `khach - khong canh bao`() = runTest {
        every { sessionManager.currentUid() } returns null
        profiles.flow = flowOf(profile("BEGINNER"))
        assertEquals(emptyList<DropInWarning>(), check(session))
    }
}

package com.example.booking_app.feature.dropin.data

import com.example.booking_app.feature.dropin.data.dto.RegistrationDto
import com.example.booking_app.feature.dropin.data.dto.RegistrationResultDto
import com.example.booking_app.feature.dropin.data.dto.SessionDto
import com.example.booking_app.feature.dropin.data.dto.toDateKey
import com.example.booking_app.feature.dropin.data.dto.toDomain
import com.example.booking_app.feature.dropin.domain.model.CancelledBy
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Date

class DropInMapperTest {

    private val start = Instant.parse("2026-11-12T11:00:00Z") // 18:00 +07
    private val end = Instant.parse("2026-11-12T13:00:00Z")

    private fun ts(i: Instant) = Timestamp(Date.from(i))

    private val fullDoc: Map<String, Any?> = mapOf(
        "venueId" to "v1",
        "ownerId" to "o1",
        "districtId" to "q1",
        "courtIds" to listOf("c1", "c2"),
        "date" to "20261112",
        "startAt" to ts(start),
        "endAt" to ts(end),
        "timeSlot" to "EVENING",
        "venueSnapshot" to mapOf("name" to "Sân A", "address" to "1 Lê Lợi", "districtName" to "Quận 1"),
        "level" to "INTERMEDIATE",
        "capacity" to 12L,
        "registeredCount" to 7L,
        "pricePerPerson" to 60_000L,
        "status" to "OPEN",
        "note" to "Mang giày",
    )

    @Test
    fun `buoi day du - map dung moi truong`() {
        val s = SessionDto.fromMap("s1", fullDoc).toDomain(venueActive = true, courtNames = listOf("Sân 1"))
        assertNotNull(s)
        s!!
        assertEquals("s1", s.id)
        assertEquals("v1", s.venueId)
        assertEquals("o1", s.ownerId)
        assertEquals("q1", s.districtId)
        assertEquals("Sân A", s.venueName)
        assertEquals("1 Lê Lợi", s.venueAddress)
        assertEquals("Quận 1", s.districtName)
        assertEquals(listOf("c1", "c2"), s.courtIds)
        assertEquals(listOf("Sân 1"), s.courtNames)
        assertEquals(start, s.startAt)
        assertEquals(end, s.endAt)
        assertEquals(TimeSlot.EVENING, s.timeSlot)
        assertEquals(Level.INTERMEDIATE, s.level)
        assertEquals(12, s.capacity)
        assertEquals(7, s.registeredCount)
        assertEquals(5, s.remaining)
        assertEquals(60_000L, s.pricePerPerson)
        assertEquals(SessionStatus.OPEN, s.status)
        assertEquals("Mang giày", s.note)
        assertTrue(s.venueActive)
        assertTrue(s.isOwnedBy("o1"))
        assertFalse(s.isOwnedBy("u1"))
    }

    @Test
    fun `courtId dang chuoi - doc du phong thanh courtIds`() {
        val doc = fullDoc - "courtIds" + ("courtId" to "c9")
        assertEquals(listOf("c9"), SessionDto.fromMap("s1", doc).toDomain()!!.courtIds)
    }

    @Test
    fun `level ALL, thieu hoac la - null nghia la moi trinh do`() {
        assertNull(SessionDto.fromMap("s", fullDoc + ("level" to "ALL")).toDomain()!!.level)
        assertNull(SessionDto.fromMap("s", fullDoc - "level").toDomain()!!.level)
        assertNull(SessionDto.fromMap("s", fullDoc + ("level" to 3L)).toDomain()!!.level)
    }

    @Test
    fun `status la hoac thieu - CANCELLED de khong cho dang ky`() {
        assertEquals(SessionStatus.CANCELLED, SessionDto.fromMap("s", fullDoc + ("status" to "WEIRD")).toDomain()!!.status)
        assertEquals(SessionStatus.CANCELLED, SessionDto.fromMap("s", fullDoc - "status").toDomain()!!.status)
        assertEquals(SessionStatus.FULL, SessionDto.fromMap("s", fullDoc + ("status" to "full")).toDomain()!!.status)
    }

    @Test
    fun `timeSlot thieu hoac la - tinh lai tu startAt theo gio Viet Nam`() {
        assertEquals(TimeSlot.EVENING, SessionDto.fromMap("s", fullDoc - "timeSlot").toDomain()!!.timeSlot)
        assertEquals(TimeSlot.EVENING, SessionDto.fromMap("s", fullDoc + ("timeSlot" to "NIGHT")).toDomain()!!.timeSlot)
    }

    @Test
    fun `thieu startAt - dung date va startTime HH_mm cua ban cu`() {
        val doc = fullDoc - "startAt" - "endAt" + ("startTime" to "06:30") + ("endTime" to "08:00")
        val s = SessionDto.fromMap("s", doc).toDomain()!!
        assertEquals(Instant.parse("2026-11-11T23:30:00Z"), s.startAt)
        assertEquals(Instant.parse("2026-11-12T01:00:00Z"), s.endAt)
    }

    @Test
    fun `startAt dang chuoi ISO - van doc duoc`() {
        val doc = fullDoc + ("startAt" to "2026-11-12T11:00:00Z")
        assertEquals(start, SessionDto.fromMap("s", doc).toDomain()!!.startAt)
    }

    @Test
    fun `thieu venueId hoac khong co gio - bo document`() {
        assertNull(SessionDto.fromMap("s", fullDoc - "venueId").toDomain())
        assertNull(SessionDto.fromMap("s", fullDoc - "startAt" - "date").toDomain())
        assertNull(SessionDto.fromMap("s", emptyMap()).toDomain())
    }

    @Test
    fun `so dang Double hoac thieu - khong crash, khong am`() {
        val doc = fullDoc + ("capacity" to 10.0) + ("registeredCount" to -3L) + ("pricePerPerson" to 45_000.0) - "note" - "venueSnapshot"
        val s = SessionDto.fromMap("s", doc).toDomain()!!
        assertEquals(10, s.capacity)
        assertEquals(0, s.registeredCount)
        assertEquals(45_000L, s.pricePerPerson)
        assertNull(s.note)
        assertEquals("", s.venueName)
    }

    @Test
    fun `ngay sang khoa yyyyMMdd`() {
        assertEquals("20261105", LocalDate.of(2026, 11, 5).toDateKey())
    }

    // ---------- Đăng ký ----------

    private val regDoc: Map<String, Any?> = mapOf(
        "userId" to "u1",
        "sessionId" to "s1",
        "partySize" to 2L,
        "status" to "REGISTERED",
        "paymentMethod" to "MOCK_PAY",
        "paymentStatus" to "PAID",
        "pricePerPerson" to 60_000L,
        "amount" to 120_000L,
        "qrToken" to "tok",
        "ticketCode" to "DI-7K3QXZ9M",
        "sessionChanged" to false,
        "sessionStartAt" to ts(start),
        "sessionEndAt" to ts(end),
        "registeredAt" to ts(start.minusSeconds(3600)),
    )

    @Test
    fun `dang ky day du - map dung`() {
        val r = RegistrationDto.fromMap("s1", "u1", regDoc).toDomain()!!
        assertEquals("s1", r.sessionId)
        assertEquals("u1", r.userId)
        assertEquals(2, r.partySize)
        assertEquals(RegistrationStatus.REGISTERED, r.status)
        assertEquals(PaymentMethod.MOCK_PAY, r.paymentMethod)
        assertEquals(PaymentStatus.PAID, r.paymentStatus)
        assertEquals(120_000L, r.amount)
        assertEquals("tok", r.qrToken)
        assertEquals("DI-7K3QXZ9M", r.ticketCode)
        assertEquals(start, r.sessionStartAt)
        assertNull(r.cancelledBy)
        assertTrue(r.isActive)
    }

    @Test
    fun `dang ky thieu sessionId userId - lay tu path`() {
        val r = RegistrationDto.fromMap("sX", "uX", regDoc - "sessionId" - "userId").toDomain()!!
        assertEquals("sX", r.sessionId)
        assertEquals("uX", r.userId)
    }

    @Test
    fun `dang ky status la - null, cac enum khac la thi mac dinh`() {
        assertNull(RegistrationDto.fromMap("s", "u", regDoc + ("status" to "???")).toDomain())
        val r = RegistrationDto.fromMap(
            "s", "u",
            regDoc + ("paymentMethod" to "CASH") - "paymentStatus" - "amount" + ("cancelledBy" to "ALIEN") +
                ("status" to "CANCELLED") + ("qrToken" to null),
        ).toDomain()!!
        assertEquals(PaymentMethod.AT_VENUE, r.paymentMethod)
        assertEquals(PaymentStatus.UNPAID, r.paymentStatus)
        assertEquals(120_000L, r.amount)
        assertNull(r.cancelledBy)
        assertNull(r.qrToken)
        assertFalse(r.isActive)
    }

    @Test
    fun `dang ky bi huy boi chu san`() {
        val r = RegistrationDto.fromMap("s", "u", regDoc + ("status" to "CANCELLED") + ("cancelledBy" to "OWNER")).toDomain()!!
        assertEquals(CancelledBy.OWNER, r.cancelledBy)
    }

    @Test
    fun `response register-drop-in - khong co qrToken, registeredAt ISO`() {
        val dto = RegistrationResultDto(
            sessionId = "s1", status = "REGISTERED", partySize = 2, paymentMethod = "AT_VENUE",
            paymentStatus = "UNPAID", pricePerPerson = 60_000, amount = 120_000, ticketCode = "DI-AAAA2222",
            registeredAt = "2026-10-20T11:00:00Z",
        )
        val r = dto.toDomain(fallbackSessionId = "other", uid = "u1")
        assertEquals("s1", r.sessionId)
        assertEquals("u1", r.userId)
        assertEquals(PaymentMethod.AT_VENUE, r.paymentMethod)
        assertEquals(PaymentStatus.UNPAID, r.paymentStatus)
        assertNull(r.qrToken)
        assertEquals(Instant.parse("2026-10-20T11:00:00Z"), r.registeredAt)
    }
}

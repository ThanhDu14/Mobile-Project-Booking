package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.dropin.data.dto.AddPartyRequest
import com.example.booking_app.feature.dropin.data.dto.CancelDropInRequest
import com.example.booking_app.feature.dropin.data.dto.CancelDropInResponse
import com.example.booking_app.feature.dropin.data.dto.CancelQuoteDto
import com.example.booking_app.feature.dropin.data.dto.DropInFunctionJson
import com.example.booking_app.feature.dropin.data.dto.RegisterDropInRequest
import com.example.booking_app.feature.dropin.data.dto.RegisterDropInResponse
import com.example.booking_app.feature.dropin.data.dto.RegistrationDto
import com.example.booking_app.feature.dropin.data.dto.RegistrationResultDto
import com.example.booking_app.feature.dropin.data.dto.SessionDto
import com.example.booking_app.feature.dropin.data.dto.WaitlistDto
import com.example.booking_app.feature.dropin.data.dto.toDomain
import com.example.booking_app.feature.dropin.data.dto.toDropInError
import com.example.booking_app.feature.dropin.domain.model.CancelQuote
import com.example.booking_app.feature.dropin.domain.model.CancelReason
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistEndReason
import com.example.booking_app.feature.dropin.domain.model.WaitlistPosition
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Date

/** T013, T020, T040: DTO, mapper, mã lỗi và repository của hàng chờ (spec 081 contract mục 1, 2, 6). */
class WaitlistDataTest {

    private class FakeApi : DropInApi {
        val requests = mutableListOf<RegisterDropInRequest>()
        var next: () -> RegisterDropInResponse = { error("chưa đặt") }
        override suspend fun register(request: RegisterDropInRequest): RegisterDropInResponse {
            requests += request
            return next()
        }

        override suspend fun addParty(request: AddPartyRequest): RegisterDropInResponse = error("không dùng")

        val cancelRequests = mutableListOf<CancelDropInRequest>()
        var nextCancel: () -> CancelDropInResponse = { error("chưa đặt") }
        override suspend fun cancel(request: CancelDropInRequest): CancelDropInResponse {
            cancelRequests += request
            return nextCancel()
        }
    }

    private val api = FakeApi()
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val session = mockk<SessionManager> { every { currentUid() } returns "u1" }
    private val repo = DropInRegistrationRepositoryImpl(api, firestore, session)

    private val req = RegisterRequest("s1", 2, PaymentMethod.AT_VENUE, "3b9e2c1a-0000-4000-8000-000000000001", joinWaitlist = true)

    private fun http(status: Int, code: String, extra: String = ""): Throwable =
        AppError.Unknown(FunctionHttpException(status, """{"error":{"code":"$code","message":"x"$extra}}"""))

    private fun waitlistedResponse(position: Int = 3, total: Int = 3) = RegisterDropInResponse(
        registration = RegistrationResultDto(
            sessionId = "s1", status = "WAITLISTED", partySize = 2, paymentMethod = "AT_VENUE",
            pricePerPerson = 60_000, amount = 120_000, waitlistedAt = "2026-11-12T03:00:00Z",
        ),
        waitlist = WaitlistDto(position = position, total = total),
    )

    // ---------- Repository: vào hàng chờ ----------

    @Test
    fun `vao hang cho - gui joinWaitlist true va map vi tri`() = runTest {
        api.next = { waitlistedResponse(position = 2, total = 3) }

        val result = repo.joinWaitlist(req).getOrThrow()

        val sent = api.requests.single()
        assertEquals(RegisterDropInRequest("s1", 2, "AT_VENUE", req.requestId, joinWaitlist = true), sent)
        assertEquals(RegistrationStatus.WAITLISTED, result.registration.status)
        assertEquals(120_000L, result.registration.amount)
        assertEquals(WaitlistPosition(2, 3), result.position)
        assertNull(result.registration.qrToken)
    }

    @Test
    fun `dang ky thuong khong gui truong joinWaitlist`() {
        val normal = DropInFunctionJson.encodeToString(RegisterDropInRequest.serializer(), RegisterDropInRequest("s1", 1, "MOCK_PAY", "r"))
        assertFalse(normal.contains("joinWaitlist"))

        val waitlist = DropInFunctionJson.encodeToString(
            RegisterDropInRequest.serializer(),
            RegisterDropInRequest("s1", 1, "MOCK_PAY", "r", joinWaitlist = true),
        )
        assertTrue(waitlist.contains("\"joinWaitlist\":true"))
    }

    @Test
    fun `response thieu waitlist - that bai INVALID_RESPONSE de thu lai cung requestId`() = runTest {
        api.next = { RegisterDropInResponse(RegistrationResultDto(sessionId = "s1", status = "WAITLISTED")) }

        val error = repo.joinWaitlist(req).exceptionOrNull()

        assertEquals(DropInError.Unknown("INVALID_RESPONSE"), error)
    }

    @Test
    fun `gui lai sau khi da duoc day len - response khong co waitlist thi AlreadyRegistered`() = runTest {
        api.next = {
            RegisterDropInResponse(
                RegistrationResultDto(sessionId = "s1", status = "REGISTERED", partySize = 2, paymentMethod = "AT_VENUE"),
            )
        }

        assertTrue(repo.joinWaitlist(req).exceptionOrNull() is DropInError.AlreadyRegistered)
    }

    @Test
    fun `vao hang cho - cac ma loi moi`() = runTest {
        api.next = { throw http(409, "SESSION_HAS_SPOTS", ""","remaining":2""") }
        assertEquals(DropInError.SessionHasSpots(2), repo.joinWaitlist(req).exceptionOrNull())

        api.next = { throw http(409, "WAITLIST_CLOSED") }
        assertTrue(repo.joinWaitlist(req).exceptionOrNull() is DropInError.WaitlistClosed)

        api.next = { throw http(409, "ALREADY_WAITLISTED") }
        assertTrue(repo.joinWaitlist(req).exceptionOrNull() is DropInError.AlreadyWaitlisted)

        api.next = { throw AppError.Network }
        assertTrue(repo.joinWaitlist(req).exceptionOrNull() is DropInError.Network)
    }

    @Test
    fun `vao hang cho khi chua dang nhap - Unauthenticated, khong goi function`() = runTest {
        every { session.currentUid() } returns null

        assertTrue(repo.joinWaitlist(req).exceptionOrNull() is DropInError.Unauthenticated)
        assertTrue(api.requests.isEmpty())
    }

    // ---------- Repository: rời hàng chờ ----------

    @Test
    fun `roi hang cho - gui expectWaitlisted true, dryRun false va map luot da huy`() = runTest {
        api.nextCancel = {
            CancelDropInResponse(
                registration = RegistrationResultDto(
                    sessionId = "s1", status = "CANCELLED", partySize = 2, paymentMethod = "AT_VENUE",
                    waitlistEndReason = "LEFT",
                ),
                quote = CancelQuoteDto(refundAmount = 0, reason = "WAITLIST_LEAVE"),
            )
        }

        val result = repo.leaveWaitlist("s1", "r1").getOrThrow()

        assertEquals(CancelDropInRequest("s1", "r1", dryRun = false, expectWaitlisted = true), api.cancelRequests.single())
        assertEquals(RegistrationStatus.CANCELLED, result.status)
        assertEquals(WaitlistEndReason.LEFT, result.waitlistEndReason)
    }

    @Test
    fun `huy thuong khong gui truong expectWaitlisted`() {
        val body = DropInFunctionJson.encodeToString(CancelDropInRequest.serializer(), CancelDropInRequest("s1", "r1"))
        assertFalse(body.contains("expectWaitlisted"))
        assertTrue(body.contains("\"dryRun\":false"))
    }

    @Test
    fun `roi hang cho - ALREADY_PROMOTED va mat mang`() = runTest {
        api.nextCancel = { throw http(409, "ALREADY_PROMOTED") }
        assertTrue(repo.leaveWaitlist("s1", "r1").exceptionOrNull() is DropInError.AlreadyPromoted)

        api.nextCancel = { throw AppError.Network }
        assertTrue(repo.leaveWaitlist("s1", "r1").exceptionOrNull() is DropInError.Network)

        api.nextCancel = { CancelDropInResponse() }
        assertEquals(DropInError.Unknown("INVALID_RESPONSE"), repo.leaveWaitlist("s1", "r1").exceptionOrNull())
    }

    @Test
    fun `quote WAITLIST_LEAVE va PROMOTED_GRACE duoc map`() {
        assertEquals(CancelQuote(0, CancelReason.WAITLIST_LEAVE), CancelQuoteDto(0, "WAITLIST_LEAVE").toDomain())
        assertEquals(CancelQuote(120_000, CancelReason.PROMOTED_GRACE), CancelQuoteDto(120_000, "PROMOTED_GRACE").toDomain())
    }

    @Test
    fun `response huy doc duoc promotedCount`() {
        val body = """{"registration":{"status":"CANCELLED"},"quote":{"refundAmount":0,"reason":"EARLY"},"promotedCount":2}"""
        val parsed = DropInFunctionJson.decodeFromString(CancelDropInResponse.serializer(), body)
        assertEquals(2, parsed.promotedCount)
    }

    // ---------- Mã lỗi ----------

    @Test
    fun `SESSION_FULL kem waitlistOpen`() {
        assertEquals(
            DropInError.SessionFull(0, waitlistOpen = true),
            http(409, "SESSION_FULL", ""","remaining":0,"waitlistOpen":true""").toDropInError(),
        )
        assertEquals(
            DropInError.SessionFull(0, waitlistOpen = false),
            http(409, "SESSION_FULL", ""","remaining":0,"waitlistOpen":false""").toDropInError(),
        )
        assertEquals(DropInError.SessionFull(1, waitlistOpen = false), http(409, "SESSION_FULL", ""","remaining":1""").toDropInError())
    }

    @Test
    fun `ma loi hang cho - dung lop DropInError`() {
        assertEquals(DropInError.SessionHasSpots(3), http(409, "SESSION_HAS_SPOTS", ""","remaining":3""").toDropInError())
        assertEquals(DropInError.SessionHasSpots(0), http(409, "SESSION_HAS_SPOTS").toDropInError())
        assertTrue(http(409, "WAITLIST_CLOSED").toDropInError() is DropInError.WaitlistClosed)
        assertTrue(http(409, "ALREADY_PROMOTED").toDropInError() is DropInError.AlreadyPromoted)
        assertTrue(http(409, "ALREADY_WAITLISTED").toDropInError() is DropInError.AlreadyWaitlisted)
    }

    // ---------- Mapper Firestore ----------

    private val start = Instant.parse("2026-11-12T11:00:00Z")

    private fun ts(i: Instant) = Timestamp(Date.from(i))

    @Test
    fun `buoi doc waitlistTickets va waitlistCount, thieu thi rong`() {
        val base = mapOf<String, Any?>("venueId" to "v1", "startAt" to ts(start), "endAt" to ts(start.plusSeconds(7200)))

        val withTickets = SessionDto.fromMap("s1", base + mapOf("waitlistTickets" to listOf(3L, 5L, 9.0), "waitlistCount" to 3L))
            .toDomain()!!
        assertEquals(listOf(3, 5, 9), withTickets.waitlistTickets)
        assertEquals(3, withTickets.waitlistCount)

        val missing = SessionDto.fromMap("s1", base).toDomain()!!
        assertTrue(missing.waitlistTickets.isEmpty())
        assertEquals(0, missing.waitlistCount)
    }

    @Test
    fun `waitlistCount lech voi mang thi lay theo mang`() {
        val base = mapOf<String, Any?>("venueId" to "v1", "startAt" to ts(start), "endAt" to ts(start.plusSeconds(7200)))

        val s = SessionDto.fromMap("s1", base + mapOf("waitlistTickets" to listOf(1L, 2L), "waitlistCount" to 7L)).toDomain()!!

        assertEquals(2, s.waitlistCount)
    }

    @Test
    fun `luot cho doc waitlistTicket, promotedAt, waitlistEndReason`() {
        val doc = mapOf<String, Any?>(
            "status" to "CANCELLED",
            "partySize" to 2L,
            "paymentMethod" to "AT_VENUE",
            "pricePerPerson" to 60_000L,
            "waitlistTicket" to 5L,
            "promotedAt" to ts(start),
            "waitlistEndReason" to "AUTO_LEFT_OVERLAP",
            "cancelledBy" to "SYSTEM",
        )

        val r = RegistrationDto.fromMap("s1", "u1", doc).toDomain()!!

        assertEquals(5, r.waitlistTicket)
        assertEquals(start, r.promotedAt)
        assertEquals(WaitlistEndReason.AUTO_LEFT_OVERLAP, r.waitlistEndReason)
        assertTrue(r.isFromWaitlist)
    }

    @Test
    fun `luot thuong khong co truong hang cho`() {
        val r = RegistrationDto.fromMap("s1", "u1", mapOf("status" to "REGISTERED", "partySize" to 1L)).toDomain()!!

        assertNull(r.waitlistTicket)
        assertNull(r.promotedAt)
        assertNull(r.waitlistEndReason)
        assertFalse(r.isFromWaitlist)
    }

    @Test
    fun `waitlistEndReason la hoac thieu thi null`() {
        val r = RegistrationDto.fromMap("s1", "u1", mapOf("status" to "CANCELLED", "waitlistEndReason" to "???")).toDomain()!!
        assertNull(r.waitlistEndReason)
    }
}

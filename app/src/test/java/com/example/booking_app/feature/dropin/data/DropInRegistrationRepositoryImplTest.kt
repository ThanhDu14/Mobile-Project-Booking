package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.dropin.data.dto.CancelDropInRequest
import com.example.booking_app.feature.dropin.data.dto.CancelDropInResponse
import com.example.booking_app.feature.dropin.data.dto.RegisterDropInRequest
import com.example.booking_app.feature.dropin.data.dto.RegisterDropInResponse
import com.example.booking_app.feature.dropin.data.dto.RegistrationResultDto
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DropInRegistrationRepositoryImplTest {

    private class FakeApi : DropInApi {
        val requests = mutableListOf<RegisterDropInRequest>()
        var next: () -> RegisterDropInResponse = { error("chưa đặt") }
        override suspend fun register(request: RegisterDropInRequest): RegisterDropInResponse {
            requests += request
            return next()
        }
        override suspend fun cancel(request: CancelDropInRequest): CancelDropInResponse = error("tuần 7")
    }

    private val api = FakeApi()
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val session = mockk<SessionManager> { every { currentUid() } returns "u1" }
    private val repo = DropInRegistrationRepositoryImpl(api, firestore, session)

    private val req = RegisterRequest("s1", 2, PaymentMethod.MOCK_PAY, "3b9e2c1a-0000-4000-8000-000000000001")

    @Test
    fun `dang ky thanh cong - gui dung body va map response`() = runTest {
        api.next = {
            RegisterDropInResponse(
                RegistrationResultDto(
                    sessionId = "s1", status = "REGISTERED", partySize = 2, paymentMethod = "MOCK_PAY",
                    paymentStatus = "PAID", pricePerPerson = 60_000, amount = 120_000, ticketCode = "DI-7K3QXZ9M",
                ),
            )
        }
        val result = repo.register(req).getOrThrow()
        assertEquals(RegisterDropInRequest("s1", 2, "MOCK_PAY", req.requestId), api.requests.single())
        assertEquals(RegistrationStatus.REGISTERED, result.status)
        assertEquals(PaymentStatus.PAID, result.paymentStatus)
        assertEquals(120_000L, result.amount)
        assertEquals("u1", result.userId)
        assertNull(result.qrToken)
    }

    @Test
    fun `loi nghiep vu - Result failure DropInError`() = runTest {
        api.next = {
            throw AppError.Unknown(
                FunctionHttpException(409, """{"error":{"code":"SESSION_FULL","message":"x","remaining":1}}"""),
            )
        }
        assertEquals(DropInError.SessionFull(1), repo.register(req).exceptionOrNull())
    }

    @Test
    fun `mat mang - Network`() = runTest {
        api.next = { throw AppError.Network }
        assertTrue(repo.register(req).exceptionOrNull() is DropInError.Network)
    }

    @Test
    fun `chua dang nhap - Unauthenticated, khong goi function`() = runTest {
        every { session.currentUid() } returns null
        assertTrue(repo.register(req).exceptionOrNull() is DropInError.Unauthenticated)
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `khach - observeMine phat null, khong nghe Firestore`() = runTest {
        every { session.currentUid() } returns null
        assertNull(repo.observeMine("s1").first())
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `phan tuan 7 - chua ho tro`() = runTest {
        assertTrue(repo.quoteCancel("s1", "r").exceptionOrNull() is UnsupportedOperationException)
        assertTrue(repo.cancel("s1", "r").exceptionOrNull() is UnsupportedOperationException)
        assertTrue(repo.pagePast("u1", null).exceptionOrNull() is UnsupportedOperationException)
        assertEquals(emptyList<Any>(), repo.observeUpcoming("u1").toList())
    }
}

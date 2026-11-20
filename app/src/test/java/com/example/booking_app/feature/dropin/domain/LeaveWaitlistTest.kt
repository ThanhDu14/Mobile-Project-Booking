package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistJoinResult
import com.example.booking_app.feature.dropin.domain.model.WaitlistPosition
import com.example.booking_app.feature.dropin.domain.usecase.JoinWaitlistImpl
import com.example.booking_app.feature.dropin.domain.usecase.LeaveWaitlistImpl
import com.example.booking_app.feature.dropin.ui.registration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T021, T037: use case vào và rời hàng chờ kiểm tra nhanh ở app rồi gọi repository. */
class LeaveWaitlistTest {

    private val repo = mockk<DropInRegistrationRepository>()

    @Test
    fun `roi hang cho - thieu sessionId hoac requestId bi tu choi, khong goi repository`() = runTest {
        val useCase = LeaveWaitlistImpl(repo)

        assertTrue(useCase("", "r1").exceptionOrNull() is DropInError.InvalidArgument)
        assertTrue(useCase("s1", " ").exceptionOrNull() is DropInError.InvalidArgument)
        coVerify(exactly = 0) { repo.leaveWaitlist(any(), any()) }
    }

    @Test
    fun `roi hang cho - hop le thi chuyen sang repository va giu nguyen loi`() = runTest {
        val useCase = LeaveWaitlistImpl(repo)
        val left = registration(status = RegistrationStatus.CANCELLED, qrToken = null)
        coEvery { repo.leaveWaitlist("s1", "r1") } returns Result.success(left)
        assertEquals(left, useCase("s1", "r1").getOrThrow())

        coEvery { repo.leaveWaitlist("s1", "r2") } returns Result.failure(DropInError.AlreadyPromoted())
        assertTrue(useCase("s1", "r2").exceptionOrNull() is DropInError.AlreadyPromoted)
    }

    @Test
    fun `vao hang cho - so nguoi ngoai 1 den 4 bi tu choi o app, 4 nguoi hop le`() = runTest {
        val useCase = JoinWaitlistImpl(repo)
        listOf(0, 5).forEach { n ->
            val r = useCase(RegisterRequest("s1", n, PaymentMethod.MOCK_PAY, "req", joinWaitlist = true))
            assertTrue(r.exceptionOrNull() is DropInError.InvalidArgument)
        }
        coVerify(exactly = 0) { repo.joinWaitlist(any()) }

        val joined = WaitlistJoinResult(
            registration(status = RegistrationStatus.WAITLISTED, partySize = 4, qrToken = null),
            WaitlistPosition(1, 1),
        )
        coEvery { repo.joinWaitlist(any()) } returns Result.success(joined)
        assertEquals(joined, useCase(RegisterRequest("s1", 4, PaymentMethod.AT_VENUE, "req", joinWaitlist = true)).getOrThrow())
    }

    @Test
    fun `vao hang cho - luon gui joinWaitlist true du request thieu co`() = runTest {
        val useCase = JoinWaitlistImpl(repo)
        coEvery { repo.joinWaitlist(any()) } returns Result.failure(DropInError.WaitlistClosed())

        useCase(RegisterRequest("s1", 1, PaymentMethod.MOCK_PAY, "req"))

        coVerify { repo.joinWaitlist(match { it.joinWaitlist }) }
    }
}

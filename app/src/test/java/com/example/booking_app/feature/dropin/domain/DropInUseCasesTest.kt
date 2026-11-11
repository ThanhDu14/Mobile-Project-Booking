package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.example.booking_app.feature.dropin.domain.usecase.GetDefaultDropInFilterImpl
import com.example.booking_app.feature.dropin.domain.usecase.LoadDropInPageImpl
import com.example.booking_app.feature.dropin.domain.usecase.RegisterDropInImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DropInUseCasesTest {

    private fun profile(districts: List<String>) = MyProfile(
        displayName = "P", level = null, favoriteDistrictIds = districts, avatarPath = null, role = "PLAYER",
        accountStatus = "ACTIVE",
    )

    private fun reader(f: Flow<MyProfile?>) = object : UserProfileReader {
        override fun observeMyProfile(): Flow<MyProfile?> = f
    }

    private val sessionManager = mockk<SessionManager> { every { currentUid() } returns "u1" }

    @Test
    fun `bo loc mac dinh - khu vuc yeu thich, toi da 15`() = runTest {
        val ids = (1..20).map { "d$it" }
        val filter = GetDefaultDropInFilterImpl(sessionManager, reader(flowOf(profile(ids + "d1" + "")))).invoke()
        assertEquals(ids.take(15).toSet(), filter.districtIds)
        assertEquals(null, filter.validate())
    }

    @Test
    fun `bo loc mac dinh - khach, chua co ho so hoac loi thi rong`() = runTest {
        assertTrue(GetDefaultDropInFilterImpl(sessionManager, reader(flowOf(null))).invoke().isEmpty)
        assertTrue(GetDefaultDropInFilterImpl(sessionManager, reader(flow { error("x") })).invoke().isEmpty)
        every { sessionManager.currentUid() } returns null
        assertTrue(GetDefaultDropInFilterImpl(sessionManager, reader(flowOf(profile(listOf("q1"))))).invoke().isEmpty)
    }

    @Test
    fun `tai trang - bo loc qua 15 quan bi tu choi, khong goi repository`() = runTest {
        val repo = mockk<DropInSessionRepository>()
        val tooMany = DropInFilter(districtIds = (1..16).map { "d$it" }.toSet())
        assertTrue(LoadDropInPageImpl(repo).invoke(tooMany, null).exceptionOrNull() is DropInError.InvalidArgument)
        coVerify(exactly = 0) { repo.page(any(), any()) }

        coEvery { repo.page(any(), any()) } returns Result.success(Page(emptyList(), null))
        assertTrue(LoadDropInPageImpl(repo).invoke(DropInFilter(), null).isSuccess)
    }

    @Test
    fun `dang ky - so nguoi ngoai 1 den 4 bi tu choi o app`() = runTest {
        val repo = mockk<DropInRegistrationRepository>()
        val useCase = RegisterDropInImpl(repo)
        listOf(0, 5).forEach { n ->
            val r = useCase(RegisterRequest("s1", n, PaymentMethod.MOCK_PAY, "req"))
            assertTrue(r.exceptionOrNull() is DropInError.InvalidArgument)
        }
        coVerify(exactly = 0) { repo.register(any()) }

        coEvery { repo.register(any()) } returns Result.failure(DropInError.Busy())
        assertTrue(useCase(RegisterRequest("s1", 4, PaymentMethod.AT_VENUE, "req")).exceptionOrNull() is DropInError.Busy)
        coVerify(exactly = 1) { repo.register(any()) }
    }
}

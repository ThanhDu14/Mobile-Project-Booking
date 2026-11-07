package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.FakeSessionPrefs
import com.example.booking_app.core.session.PendingSignup
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.FakeOnSignupApi
import com.example.booking_app.feature.auth.data.AuthRepositoryImpl
import com.example.booking_app.feature.auth.data.FirebaseAuthDataSource
import com.example.booking_app.feature.auth.data.OnSignupRequest
import com.example.booking_app.feature.auth.data.OnSignupResponse
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegisterWithEmailUseCaseTest {

    private val repository = FakeAuthRepository().apply { signUpResult = AuthResult.Success("u1") }
    private val prefs = FakeSessionPrefs()
    private val sessionManager = FakeSessionManager(SessionState.SignedOut(null))
    private val api = FakeOnSignupApi()
    private val clock = FakeClock { 1_000L }

    private val useCase = RegisterWithEmailUseCase(
        repository = repository,
        prefs = prefs,
        resendVerification = ResendVerificationEmailUseCase(repository, prefs, clock),
        completeSignup = CompleteSignupUseCase(api, sessionManager, prefs),
        clock = clock,
    )

    @Test
    fun `email duoc trim va viet thuong truoc khi tao`() = runTest {
        useCase(name = "Lê Hoàng Nam", email = "  Nam.Le@Email.COM ", password = "abc12345")
        assertEquals(listOf("nam.le@email.com" to "abc12345"), repository.signUpCalls)
    }

    @Test
    fun `tao tai khoan, luu PendingSignup, gui email xac minh roi khoi tao`() = runTest {
        var pendingAtSignup: PendingSignup? = null
        var verificationSentBeforeSignup = false
        api.result = {
            verificationSentBeforeSignup = repository.verificationCalls == 1
            pendingAtSignup = prefs.pending
            OnSignupResponse("CREATED", "PLAYER")
        }

        val result = useCase(name = " Lê Hoàng Nam ", email = "nam@email.com", password = "abc12345")

        assertEquals(CompleteSignupResult.Success(AppRole.PLAYER), result)
        assertEquals(listOf("signUp", "sendEmailVerification"), repository.calls)
        // PendingSignup có trước khi gọi on-signup, để mở lại app vẫn khởi tạo tiếp được (research R4).
        assertEquals(PendingSignup("u1", "Lê Hoàng Nam", 1_000L), pendingAtSignup)
        assertEquals(true, verificationSentBeforeSignup)
        assertEquals(listOf(OnSignupRequest("Lê Hoàng Nam", true)), api.requests)
        assertEquals(1_000L, prefs.lastVerificationEmailAt)
        // on-signup xong thì xóa PendingSignup.
        assertNull(prefs.pending)
    }

    @Test
    fun `gui email xac minh loi thi bo qua, van khoi tao`() = runTest {
        repository.verificationResult = AuthResult.Failure(AuthError.Network)
        val result = useCase("Nam Lê", "nam@email.com", "abc12345")
        assertEquals(CompleteSignupResult.Success(AppRole.PLAYER), result)
        assertEquals(1, api.requests.size)
        // Không ghi mốc khi gửi lỗi: banner cho gửi lại ngay.
        assertNull(prefs.lastVerificationEmailAt)
    }

    @Test
    fun `EmailInUse thi khong luu PendingSignup, khong khoi tao`() = runTest {
        repository.signUpResult = AuthResult.Failure(AuthError.EmailInUse)
        val result = useCase("Nam Lê", "nam@email.com", "abc12345")
        assertEquals(CompleteSignupResult.Failure(AuthError.EmailInUse), result)
        assertNull(prefs.pending)
        assertEquals(0, repository.verificationCalls)
        assertEquals(0, api.requests.size)
    }

    @Test
    fun `on-signup mat mang thi tra Network va giu PendingSignup de thu lai`() = runTest {
        api.result = { throw AppError.Network }
        val result = useCase("Nam Lê", "nam@email.com", "abc12345")
        assertEquals(CompleteSignupResult.Failure(AuthError.Network), result)
        assertEquals("u1", prefs.pending?.uid)
    }

    @Test
    fun `thu lai thi khong gui lai email xac minh da gui`() = runTest {
        api.result = { throw AppError.Network }
        useCase("Nam Lê", "nam@email.com", "abc12345")
        api.result = { OnSignupResponse("CREATED", "PLAYER") }
        useCase("Nam Lê", "nam@email.com", "abc12345")
        assertEquals(1, repository.verificationCalls)
    }

    @Test
    fun `thu lai sau timeout khi tai khoan da tao thi khong tao lai (R13)`() = runTest {
        val dataSource = mockk<FirebaseAuthDataSource>(relaxed = true)
        every { dataSource.currentUid } returns "u1"
        every { dataSource.currentEmail } returns "nam@email.com"
        val result = AuthRepositoryImpl(dataSource).signUpWithEmail("nam@email.com", "abc12345")
        assertEquals(AuthResult.Success("u1"), result)
        coVerify(exactly = 0) { dataSource.createUser(any(), any()) }
    }

    @Test
    fun `chua co tai khoan hoac email khac thi tao moi`() = runTest {
        val dataSource = mockk<FirebaseAuthDataSource>(relaxed = true)
        every { dataSource.currentUid } returns null
        every { dataSource.currentEmail } returns null
        coEvery { dataSource.createUser("nam@email.com", "abc12345") } returns "u2"
        assertEquals(AuthResult.Success("u2"), AuthRepositoryImpl(dataSource).signUpWithEmail("nam@email.com", "abc12345"))
    }
}

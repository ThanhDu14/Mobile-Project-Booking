package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.feature.account.FakeIdTokenProvider
import com.example.booking_app.feature.account.FakeReauthDataSource
import com.example.booking_app.feature.account.data.ReauthAccount
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.ReauthMethod
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ReauthenticateUseCaseTest {

    private val dataSource = FakeReauthDataSource()
    private val tokens = FakeIdTokenProvider()
    private val useCase = ReauthenticateUseCase(dataSource, tokens)

    private fun account(vararg providers: String, phone: String? = null) =
        ReauthAccount(providers = providers.toSet(), email = "a@b.vn", phoneE164 = phone)

    @Test
    fun `co password thi xac nhan bang mat khau`() {
        dataSource.account = account("password")
        assertEquals(ReauthMethod.Password, useCase.method())
    }

    @Test
    fun `chi Google thi xac nhan bang Google`() {
        dataSource.account = account("google.com")
        assertEquals(ReauthMethod.Google, useCase.method())
    }

    @Test
    fun `chi so dien thoai thi xac nhan bang OTP toi so hien tai`() {
        dataSource.account = account("phone", phone = "+84901234567")
        assertEquals(ReauthMethod.Phone("+84901234567"), useCase.method())
    }

    @Test
    fun `nhieu phuong thuc thi uu tien password roi Google roi so dien thoai`() {
        dataSource.account = account("phone", "google.com", "password", phone = "+84901234567")
        assertEquals(ReauthMethod.Password, useCase.method())
        dataSource.account = account("phone", "google.com", phone = "+84901234567")
        assertEquals(ReauthMethod.Google, useCase.method())
    }

    @Test
    fun `chua dang nhap thi khong co phuong thuc`() {
        dataSource.account = null
        assertNull(useCase.method())
    }

    @Test
    fun `xac nhan bang mat khau thanh cong thi ep lam moi token`() = runTest {
        useCase.withPassword("Matkhau123")
        assertEquals(listOf("password:Matkhau123"), dataSource.calls)
        assertEquals(listOf(true), tokens.forceRefreshCalls)
    }

    @Test
    fun `xac nhan bang Google va OTP thanh cong thi ep lam moi token`() = runTest {
        useCase.withGoogle("id-token")
        useCase.withPhoneCode("vid", "123456")
        assertEquals(listOf("google:id-token", "phone:vid:123456"), dataSource.calls)
        assertEquals(listOf(true, true), tokens.forceRefreshCalls)
    }

    @Test
    fun `may tu xac minh OTP thi chi lam moi token`() = runTest {
        useCase.afterPhoneAutoVerified()
        assertEquals(emptyList<String>(), dataSource.calls)
        assertEquals(listOf(true), tokens.forceRefreshCalls)
    }

    @Test
    fun `sai mat khau thi nem WrongCurrentPassword va khong lam moi token`() = runTest {
        dataSource.failure = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "x")
        try {
            useCase.withPassword("sai")
            fail("phải ném lỗi")
        } catch (e: AccountError) {
            assertEquals(AccountError.WrongCurrentPassword, e)
        }
        assertEquals(emptyList<Boolean>(), tokens.forceRefreshCalls)
    }
}

package com.example.booking_app.core.network

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class FunctionErrorsTest {

    private val idTokenProvider = mockk<IdTokenProvider> {
        coEvery { getToken(any()) } returns "token"
    }
    private val lockedBus = AccountLockedBus()
    private val functionErrors = FunctionErrors(idTokenProvider, lockedBus)

    private suspend fun expectError(block: suspend () -> Unit): AppError {
        try {
            block()
        } catch (e: AppError) {
            return e
        }
        fail("Phải ném AppError")
        error("unreachable")
    }

    @Test
    fun `403 ACCOUNT_LOCKED thanh Locked va bao AccountLockedBus`() = runTest {
        lockedBus.events.test {
            val error = expectError {
                functionErrors.invokeWithAuthRetry<Unit> {
                    throw FunctionHttpException(403, """{ "error": { "code": "ACCOUNT_LOCKED", "message": "x" } }""")
                }
            }
            assertSame(AppError.Locked, error)
            awaitItem()
        }
    }

    @Test
    fun `401 thi lam moi token va thu lai dung mot lan`() = runTest {
        var calls = 0
        val result = functionErrors.invokeWithAuthRetry {
            calls++
            if (calls == 1) throw FunctionHttpException(401, null)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, calls)
        coVerify(exactly = 1) { idTokenProvider.getToken(true) }
    }

    @Test
    fun `401 lan thu hai thi khong thu lai nua`() = runTest {
        var calls = 0
        val error = expectError {
            functionErrors.invokeWithAuthRetry<Unit> {
                calls++
                throw FunctionHttpException(401, null)
            }
        }
        assertEquals(2, calls)
        assertSame(AppError.NotSignedIn, error)
        coVerify(exactly = 1) { idTokenProvider.getToken(true) }
    }

    @Test
    fun `429 thanh RateLimited`() = runTest {
        val error = expectError {
            functionErrors.invokeWithAuthRetry<Unit> { throw FunctionHttpException(429, null) }
        }
        assertSame(AppError.RateLimited, error)
    }

    @Test
    fun `loi IO thanh Network`() = runTest {
        val error = expectError {
            functionErrors.invokeWithAuthRetry<Unit> { throw IOException("offline") }
        }
        assertSame(AppError.Network, error)
    }

    @Test
    fun `ma loi khac giu body trong Unknown`() = runTest {
        val error = expectError {
            functionErrors.invokeWithAuthRetry<Unit> {
                throw FunctionHttpException(400, """{ "error": { "code": "TERMS_REQUIRED" } }""")
            }
        }
        assertTrue(error is AppError.Unknown)
        assertEquals("TERMS_REQUIRED", parseFunctionErrorCode("""{ "error": { "code": "TERMS_REQUIRED" } }"""))
    }

    @Test
    fun `body khong phai JSON thi khong co ma loi`() {
        assertEquals(null, parseFunctionErrorCode("<html>"))
        assertEquals(null, parseFunctionErrorCode(null))
    }
}

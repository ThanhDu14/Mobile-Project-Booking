package com.example.booking_app.core.network

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskAwaitTest {

    private suspend fun <T> expectError(block: suspend () -> T): AppError {
        try {
            block()
        } catch (e: AppError) {
            return e
        }
        fail("Phải ném AppError")
        error("unreachable")
    }

    @Test
    fun `task xong truoc 15 giay thi tra ket qua`() = runTest {
        val source = TaskCompletionSource<String>()
        launch {
            delay(14_000)
            source.setResult("ok")
        }
        assertEquals("ok", source.task.awaitWithTimeout())
    }

    @Test
    fun `qua 15 giay thi nem Timeout`() = runTest {
        val source = TaskCompletionSource<String>()
        val error = expectError { source.task.awaitWithTimeout() }
        assertSame(AppError.Timeout, error)
        assertEquals(15_000L, testScheduler.currentTime)
    }

    @Test
    fun `loi mang cua Firebase thanh Network`() = runTest {
        val source = TaskCompletionSource<String>()
        source.setException(FirebaseNetworkException("offline"))
        assertSame(AppError.Network, expectError { source.task.awaitWithTimeout() })
    }

    @Test
    fun `qua nhieu yeu cau thanh RateLimited`() = runTest {
        val source = TaskCompletionSource<String>()
        source.setException(FirebaseTooManyRequestsException("slow down"))
        assertSame(AppError.RateLimited, expectError { source.task.awaitWithTimeout() })
    }

    @Test
    fun `tai khoan bi tat thanh Locked`() = runTest {
        val source = TaskCompletionSource<String>()
        source.setException(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled"))
        assertSame(AppError.Locked, expectError { source.task.awaitWithTimeout() })
    }

    @Test
    fun `loi khac giu nguyen nguyen nhan trong Unknown`() = runTest {
        val source = TaskCompletionSource<String>()
        val cause = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "not found")
        source.setException(cause)
        val error = expectError { source.task.awaitWithTimeout() }
        assertTrue(error is AppError.Unknown)
        assertSame(cause, (error as AppError.Unknown).cause)
    }
}

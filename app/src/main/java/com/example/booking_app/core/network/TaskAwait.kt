package com.example.booking_app.core.network

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

const val DEFAULT_TIMEOUT_MS = 15_000L

private const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"
private const val ERROR_USER_TOKEN_EXPIRED = "ERROR_USER_TOKEN_EXPIRED"

/** Chờ một Task của Firebase, tối đa [timeoutMs] (FR-021). Mọi lỗi được đổi thành [AppError]. */
suspend fun <T> Task<T>.awaitWithTimeout(timeoutMs: Long = DEFAULT_TIMEOUT_MS): T =
    try {
        withTimeout(timeoutMs) { await() }
    } catch (e: TimeoutCancellationException) {
        throw AppError.Timeout
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toAppError()
    }

/** Đổi exception của Firebase hoặc mạng thành [AppError]. */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is FirebaseNetworkException, is IOException -> AppError.Network
    is FirebaseTooManyRequestsException -> AppError.RateLimited
    is FirebaseAuthInvalidUserException -> when (errorCode) {
        ERROR_USER_DISABLED -> AppError.Locked
        ERROR_USER_TOKEN_EXPIRED -> AppError.SessionRevoked
        else -> AppError.Unknown(this)
    }
    else -> AppError.Unknown(this)
}

package com.example.booking_app.feature.admin.data

import kotlin.coroutines.cancellation.CancellationException

/** A cancelled screen load must not become a stale error/result on the next screen. */
internal inline fun <T> adminRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

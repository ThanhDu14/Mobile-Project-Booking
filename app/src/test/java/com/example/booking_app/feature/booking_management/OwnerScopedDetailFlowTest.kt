package com.example.booking_app.feature.booking_management

import com.example.booking_app.feature.booking_management.data.ownerScopedDetailFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerScopedDetailFlowTest {
    @Test fun switchClearsOldValueAndDropsLateCallbacks() = runTest {
        val uid = MutableStateFlow<String?>("a")
        val callbacks = mutableMapOf<String, (String) -> Unit>()
        val values = mutableListOf<String?>()
        val job = launch {
            ownerScopedDetailFlow(uid, { uid.value }) { owner ->
                callbackFlow<String?> {
                    callbacks[owner] = { trySend(it) }
                    awaitClose { }
                }
            }.collect { values.add(it) }
        }
        runCurrent()
        callbacks.getValue("a")("a-detail")
        runCurrent()
        uid.value = "b"
        callbacks.getValue("a")("stale")
        runCurrent()
        assertEquals(listOf(null, "a-detail", null), values)
        callbacks.getValue("b")("b-detail")
        runCurrent()
        uid.value = null
        runCurrent()
        assertEquals(listOf(null, "a-detail", null, "b-detail", null), values)
        job.cancel()
    }
}

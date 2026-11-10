package com.example.booking_app.feature.booking_management.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

/** Clear the previous identity's detail before subscribing to the next identity. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T : Any> ownerScopedDetailFlow(
    identities: Flow<String?>,
    currentUid: () -> String?,
    listen: (String) -> Flow<T?>
): Flow<T?> = identities.distinctUntilChanged().flatMapLatest { uid ->
    flow {
        emit(uid to null)
        if (uid != null) listen(uid).collect { emit(uid to it) }
    }
}.buffer(0).transform { (uid, value) ->
    // Also guard values queued while a session switch is being processed.
    if (uid == currentUid()) emit(value)
}

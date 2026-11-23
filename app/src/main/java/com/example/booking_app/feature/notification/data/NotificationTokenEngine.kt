package com.example.booking_app.feature.notification.data

import com.example.booking_app.core.session.FcmTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

internal fun tokenHash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
interface TokenRegistrationStore {
    suspend fun register(uid: String, token: String, previousHash: String?, stillCurrent: () -> Boolean)
    suspend fun detach(uid: String, hash: String, stillCurrent: () -> Boolean)
}

/** Serial registration; epoch + session guards fence callbacks across logout/account changes. */
class NotificationTokenEngine(
    private val source: FcmTokenSource, private val store: TokenRegistrationStore, private val metadata: TokenMetadataStore,
    private val currentUid: () -> String?, private val allowed: () -> Boolean,
) {
    private val mutex = Mutex()
    @Volatile private var epoch = 0L
    @Volatile private var paused = false
    private var rotatedInEpoch: Pair<String, Long>? = null
    suspend fun beforeSignOut() {
        paused = true; epoch++
        mutex.withLock { metadata.write(metadata.read().copy(rotationRequired = true)) }
    }
    @Synchronized fun signedOut(deleted: Boolean) {
        paused = true; epoch++
        metadata.write(if (deleted) TokenMetadata() else metadata.read().copy(rotationRequired = true))
    }
    @Synchronized fun signedIn() { paused = false }
    @Synchronized private fun remember(generation: Long, uid: String, token: String) {
        if (!paused && epoch == generation && currentUid() == uid && allowed()) metadata.write(TokenMetadata(uid, tokenHash(token), false))
    }
    suspend fun sync() = mutex.withLock {
        val uid = currentUid() ?: return@withLock
        val generation = epoch
        fun valid() = !paused && currentUid() == uid && epoch == generation
        if (!valid()) return@withLock
        try {
            val previous = metadata.read()
            if (!allowed()) {
                if (previous.uid == uid) {
                    val hash = previous.hash ?: tokenHash(source.token())
                    if (valid()) store.detach(uid, hash, ::valid)
                }
                return@withLock
            }
            if ((previous.uid != uid || previous.rotationRequired) && rotatedInEpoch != (uid to generation)) {
                source.deleteToken()
                if (!valid()) return@withLock
                // Failed registration retries reuse the successful SDK rotation in this process.
                // Otherwise onNewToken could cause a delete/get/register-failure feedback loop.
                rotatedInEpoch = uid to generation
            }
            val token = source.token()
            if (!valid() || !allowed() || token.isEmpty() || token.length > 4096) return@withLock
            store.register(uid, token, previous.hash.takeIf { previous.uid == uid }) { valid() && allowed() }
            remember(generation, uid, token)
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { /* No offline queue. Network/resume/new-token retries from the current session. */ }
    }
}

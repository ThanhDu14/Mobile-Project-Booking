package com.example.booking_app.feature.notification.data

import com.example.booking_app.core.session.FcmTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NotificationTokenEngineTest {
    private class Fixture {
        var uid: String? = "a"
        var allowed = true
        var value = TokenMetadata()
        var failDelete = false
        var failRegister = false
        var deletes = 0
        var raw = "raw"
        var beforeToken: suspend () -> Unit = {}
        val registered = mutableListOf<Triple<String, String, String?>>()
        val removed = mutableListOf<String>()
        val engine = NotificationTokenEngine(object : FcmTokenSource {
            override suspend fun token(): String { beforeToken(); return raw }
            override suspend fun deleteToken() { deletes++; if (failDelete) error("offline") }
        }, object : TokenRegistrationStore {
            override suspend fun register(uid: String, token: String, previousHash: String?, stillCurrent: () -> Boolean) {
                check(stillCurrent()); if (failRegister) error("offline"); registered += Triple(uid, token, previousHash)
            }
            override suspend fun detach(uid: String, hash: String, stillCurrent: () -> Boolean) { check(stillCurrent()); removed += uid }
        }, object : TokenMetadataStore {
            override fun read() = value
            override fun write(value: TokenMetadata) { this@Fixture.value = value }
        }, { uid }, { allowed })
    }
    @Test fun guestAndDeniedNeverRegister() = runTest {
        val f = Fixture(); f.uid = null; f.engine.sync(); f.uid = "a"; f.allowed = false; f.engine.sync()
        assertTrue(f.registered.isEmpty()); assertEquals(0, f.deletes)
    }
    @Test fun firstLoginRotatesAndPersistsOnlyHashAfterAck() = runTest {
        val f = Fixture(); f.engine.sync()
        assertEquals(1, f.deletes); assertEquals(TokenMetadata("a", tokenHash("raw"), false), f.value)
        assertNotEquals("raw", f.value.hash)
    }
    @Test fun refreshUsesOldHashAndDuplicateCallbackDoesNotRotateAgain() = runTest {
        val f = Fixture(); f.engine.sync(); f.raw = "next"; f.engine.sync(); f.engine.sync()
        assertEquals(1, f.deletes); assertEquals(tokenHash("raw"), f.registered[1].third)
        assertEquals(tokenHash("next"), f.value.hash)
    }
    @Test fun failedRotationAndOfflineRegistrationDoNotRecordSuccess() = runTest {
        val f = Fixture(); f.failDelete = true; f.engine.sync(); assertTrue(f.registered.isEmpty())
        f.failDelete = false; f.failRegister = true; f.engine.sync(); assertEquals(TokenMetadata(), f.value)
        f.failRegister = false; f.engine.sync(); assertEquals("a", f.value.uid)
    }
    @Test fun offlineRegistrationRetryReusesRotationWithoutOnNewTokenFeedbackLoop() = runTest {
        val f = Fixture(); f.failRegister = true
        repeat(3) { f.engine.sync() }
        assertEquals(1, f.deletes); assertEquals(TokenMetadata(), f.value)
        f.failRegister = false; f.engine.sync(); assertEquals("a", f.value.uid)
    }
    @Test fun accountSwitchDuringSdkAwaitDropsOldCallback() = runTest {
        val f = Fixture(); val waiting = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.beforeToken = { waiting.complete(Unit); release.await() }
        val job = launch { f.engine.sync() }; waiting.await(); f.uid = "b"; f.engine.signedOut(false); release.complete(Unit); job.join()
        assertTrue(f.registered.isEmpty()); assertTrue(f.value.rotationRequired)
        f.beforeToken = {}; f.engine.signedIn(); f.engine.sync(); assertEquals("b", f.value.uid)
    }
    @Test fun signOutPausesRegistrationBeforeExistingRemovalHook() = runTest {
        val f = Fixture(); f.engine.sync(); f.engine.beforeSignOut(); f.engine.sync()
        assertEquals(1, f.registered.size); assertTrue(f.value.rotationRequired)
        f.engine.signedOut(false); f.uid = "b"; f.engine.signedIn(); f.engine.sync()
        assertEquals(2, f.deletes); assertEquals("b", f.value.uid)
    }
    @Test fun deniedThenGrantedDetachesAndRegistersWithoutBreakingSession() = runTest {
        val f = Fixture(); f.engine.sync(); f.allowed = false; f.engine.sync()
        assertEquals(listOf("a"), f.removed); assertEquals("a", f.uid)
        f.allowed = true; f.engine.sync(); assertEquals(2, f.registered.size)
    }
    @Test fun accountDeletionClearsUidAndHashButRequiresRotation() = runTest {
        val f = Fixture(); f.engine.sync(); f.engine.signedOut(true)
        assertEquals(TokenMetadata(), f.value)
    }
    @Test fun strictPushPolicyDropsForeignAccountAndPayloadExtras() {
        val data = mapOf("recipientUid" to "a", "notificationId" to "n")
        assertNotNull(NotificationPushPolicy.parse(data, "a", true))
        assertNull(NotificationPushPolicy.parse(data, "b", true))
        assertNull(NotificationPushPolicy.parse(data, null, true))
        assertNull(NotificationPushPolicy.parse(data, "a", false))
        assertNull(NotificationPushPolicy.parse(data + ("title" to "private"), "a", true))
        assertNull(NotificationPushPolicy.parse(data + ("notificationId" to "bad/path"), "a", true))
    }
}

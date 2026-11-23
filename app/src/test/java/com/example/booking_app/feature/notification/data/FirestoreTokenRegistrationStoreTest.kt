package com.example.booking_app.feature.notification.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FirestoreTokenRegistrationStoreTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val ref = mockk<DocumentReference>()
    private val tx = mockk<Transaction>()
    private val profile = mockk<DocumentSnapshot>()
    private var values: Any? = listOf("old", "other-device")
    private var active = true
    private val writes = mutableListOf<Map<String, Any>>()
    init {
        every { firestore.collection("users").document("alice") } returns ref
        every { tx.get(ref) } returns profile
        every { profile.exists() } returns true
        every { profile.getString("accountStatus") } answers { if (active) "ACTIVE" else "LOCKED" }
        every { profile.get("deletionRequestedAt") } returns null
        every { profile.get("fcmTokens") } answers { values }
        every { tx.update(ref, capture(writes)) } returns tx
        every { firestore.runTransaction(any<Transaction.Function<Unit>>()) } answers {
            Tasks.forResult(firstArg<Transaction.Function<Unit>>().apply(tx))
        }
    }
    @Test fun rotationPreservesOtherDevicesAndDuplicateCallbackIsNoOp() = runTest {
        val store = FirestoreTokenRegistrationStore(firestore)
        store.register("alice", "new", tokenHash("old")) { true }
        assertEquals(listOf("other-device", "new"), writes.single()["fcmTokens"])
        assertEquals(setOf("fcmTokens", "updatedAt"), writes.single().keys)
        values = listOf("other-device", "new")
        store.register("alice", "new", tokenHash("new")) { true }
        assertEquals(1, writes.size)
        verify(exactly = 0) { ref.update(any<Map<String, Any>>()) }
    }
    @Test fun deniedPermissionDetachesOnlyRegisteredHashEvenIfSdkTokenChanged() = runTest {
        FirestoreTokenRegistrationStore(firestore).detach("alice", tokenHash("old")) { true }
        assertEquals(listOf("other-device"), writes.single()["fcmTokens"])
    }
    @Test fun switchedOrDeletingAccountCannotRegisterAndInvalidLegacyTokensAreNotSilentlyRewritten() = runTest {
        val store = FirestoreTokenRegistrationStore(firestore)
        assertTrue(runCatching { store.register("alice", "new", null) { false } }.isFailure)
        active = false
        assertTrue(runCatching { store.register("alice", "new", null) { true } }.isFailure)
        active = true; values = listOf("old", 42)
        assertTrue(runCatching { store.register("alice", "new", null) { true } }.isFailure)
        assertTrue(writes.isEmpty())
    }
    @Test fun offlineTransactionDoesNotFallBackToQueuedDocumentUpdate() = runTest {
        every { firestore.runTransaction(any<Transaction.Function<Unit>>()) } returns Tasks.forException(IllegalStateException("offline"))
        assertTrue(runCatching { FirestoreTokenRegistrationStore(firestore).register("alice", "new", null) { true } }.isFailure)
        verify(exactly = 0) { ref.update(any<Map<String, Any>>()) }
        assertTrue(writes.isEmpty())
    }
}

package com.example.booking_app.feature.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.DataStoreSessionPrefs
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.notification.data.*
import com.example.booking_app.feature.notification.domain.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class NotificationPreferenceCacheTest {
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }
    private val store = MemoryStore()
    private val session = mockk<SessionManager>()
    private var uid: String? = "alice"
    private val cache = DataStoreNotificationPreferenceCache(store, session)
    private val fs = mockk<FirebaseFirestore>()
    private val user = mockk<DocumentReference>()
    private val profile = mockk<DocumentSnapshot>()
    private val defaults = NewAccountNotificationPreferences.defaults()
    init {
        every { session.currentUid() } answers { uid }
        every { fs.collection("users").document("alice") } returns user
        every { profile.exists() } returns true
        every { profile.get("notificationPrefs") } returns mapOf("CHAT_MESSAGE" to false)
        every { user.get(Source.SERVER) } returns Tasks.forResult(profile)
    }
    @Test fun `server read seeds cache and only network failure returns read only snapshot`() = runTest {
        val repo = FirestoreNotificationPreferencesRepository(session, fs, cache)
        assertFalse(repo.loadSnapshot().fromCache)
        every { user.get(Source.SERVER) } returns Tasks.forException(FirebaseNetworkException("offline"))
        val saved = repo.loadSnapshot()
        assertTrue(saved.fromCache)
        assertFalse(saved.values.getValue(NotificationType.CHAT_MESSAGE))
        assertFalse(saved.values.getValue(NotificationType.PROMOTION))
        assertTrue(saved.values.getValue(NotificationType.BOOKING_NEW))
        every { user.get(Source.SERVER) } returns Tasks.forResult(profile)
        assertFalse(repo.loadSnapshot().fromCache)
    }
    @Test fun `missing or forbidden profile never returns cached preferences`() = runTest {
        cache.write("alice", defaults, cache.epoch())
        every { profile.exists() } returns false
        try { FirestoreNotificationPreferencesRepository(session, fs, cache).loadSnapshot(); fail() } catch (_: IllegalStateException) {}
        every { user.get(Source.SERVER) } returns Tasks.forException(FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED))
        try { FirestoreNotificationPreferencesRepository(session, fs, cache).loadSnapshot(); fail() } catch (_: AppError) {}
    }
    @Test fun `UID isolation logout deletion cleanup and stale completion use one atomic cache epoch`() = runTest {
        val epoch = cache.epoch()
        cache.write("alice", defaults + (NotificationType.BOOKING_NEW to false), epoch)
        assertTrue(cache.read("alice", epoch)!!.getValue(NotificationType.BOOKING_NEW))
        uid = "bob"
        assertNull(cache.read("alice", epoch))
        assertNull(cache.read("bob", epoch))
        cache.write("bob", defaults, epoch)
        val prefs = DataStoreSessionPrefs(store)
        prefs.setWelcomeSeen()
        prefs.setLastPasswordResetAt(42L)
        prefs.clearOnSignOut() // Same path for normal logout and ACCOUNT_DELETED, where hooks are skipped.
        assertTrue(prefs.welcomeSeen.first())
        assertEquals(42L, prefs.lastPasswordResetAt())
        assertNull(cache.read("bob", cache.epoch()))
        uid = "alice"
        cache.write("alice", defaults, epoch) // Simulates a pre-logout SERVER operation finishing late.
        assertNull(cache.read("alice", cache.epoch()))
        assertTrue(store.data.first().asMap().keys.none { it.name.startsWith(DataStoreSessionPrefs.USER_CACHE_PREFIX) })
    }
    @Test fun `successful transaction updates cache and failed transaction preserves confirmed values`() = runTest {
        val tx = mockk<Transaction>()
        every { tx.get(user) } returns profile
        every { tx.update(user, any<Map<String, Any>>()) } returns tx
        every { fs.runTransaction(any<Transaction.Function<Map<NotificationType, Boolean>>>()) } answers {
            Tasks.forResult(firstArg<Transaction.Function<Map<NotificationType, Boolean>>>().apply(tx))
        }
        val repo = FirestoreNotificationPreferencesRepository(session, fs, cache)
        repo.setEnabled(setOf(NotificationType.PROMOTION), true)
        assertTrue(cache.read("alice", cache.epoch())!!.getValue(NotificationType.PROMOTION))
        every { fs.runTransaction(any<Transaction.Function<Map<NotificationType, Boolean>>>()) } returns Tasks.forException(FirebaseNetworkException("offline"))
        try { repo.setEnabled(setOf(NotificationType.PROMOTION), false); fail() } catch (_: AppError.Network) {}
        assertTrue(cache.read("alice", cache.epoch())!!.getValue(NotificationType.PROMOTION))
        verify(exactly = 0) { user.update(any<Map<String, Any>>()) }
    }
    @Test fun `cache IO failure does not turn a successful server read into a failed preference operation`() = runTest {
        val broken = mockk<NotificationPreferenceCache>()
        coEvery { broken.epoch() } throws IOException("disk unavailable")
        assertFalse(FirestoreNotificationPreferencesRepository(session, fs, broken).loadSnapshot().fromCache)
    }
    @Test fun `switch during server read never exposes or persists another account result`() = runTest {
        every { user.get(Source.SERVER) } answers { uid = "bob"; Tasks.forResult(profile) }
        try { FirestoreNotificationPreferencesRepository(session, fs, cache).loadSnapshot(); fail() } catch (_: IllegalStateException) {}
        uid = "alice"
        assertNull(cache.read("alice", cache.epoch()))
    }
}

package com.example.booking_app.feature.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.notification.data.FirestoreNotificationPreferencesRepository
import com.example.booking_app.feature.notification.data.FirestoreNotificationRepository
import com.example.booking_app.feature.notification.data.FirestoreNotificationRouteResolver
import com.example.booking_app.feature.notification.domain.NotificationType
import com.example.booking_app.testing.EmulatorAdmin
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/** Run against demo emulators with USB reverse ports 8080 and 9099. */
class NotificationEmulatorDeviceTest {
    @Test
    fun serverPagingPreferencesRoutingAndAccountSwitch() = runBlocking {
        withTimeout(90_000) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            assumeTrue(InstrumentationRegistry.getArguments().getString("notificationEmulator") == "true")
            check(context.packageName.endsWith(".adminlocal"))
            val options = FirebaseApp.getInstance().options
            val project = checkNotNull(options.projectId)
            check(project.startsWith("demo-"))
            val runId = UUID.randomUUID().toString()
            val app = FirebaseApp.initializeApp(context, options, "notification-device-$runId")
            val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }
            val db = FirebaseFirestore.getInstance(app).apply { useEmulator("127.0.0.1", 8080) }
            val session = object : SessionManager {
                override val sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut(null))
                override val isGuest = MutableStateFlow(false)
                override suspend fun refresh(forceTokenRefresh: Boolean) {}
                override suspend fun signOut() = auth.signOut()
                override fun currentUid() = auth.currentUser?.uid
                override fun currentAppRole() = auth.currentUser?.let { AppRole.PLAYER }
            }
            val seed = EmulatorAdmin(project, "127.0.0.1:8080")
            val inbox = FirestoreNotificationRepository(session, db)
            val routes = FirestoreNotificationRouteResolver(db, session)
            val prefs = FirestoreNotificationPreferencesRepository(session, db)
            val password = "Notification-Test-Only"
            val aliceEmail = "alice-$runId@example.test"
            suspend fun createPlayer(email: String): String {
                val uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
                val endpoint = URL("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/$project/accounts:update")
                val connection = endpoint.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer owner")
                    connection.doOutput = true
                    val payload = JSONObject().put("localId", uid)
                        .put("customAttributes", JSONObject().put("appRole", "PLAYER").toString())
                    connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                    check(connection.responseCode == 200) { "Failed to set demo PLAYER claim" }
                } finally {
                    connection.disconnect()
                }
                assertEquals("PLAYER", auth.currentUser!!.getIdToken(true).await().claims["appRole"])
                seed.put("users/$uid", mapOf("role" to "PLAYER", "accountStatus" to "ACTIVE"))
                return uid
            }
            try {
                val alice = createPlayer(aliceEmail)
                val booking = "notification-$runId"
                seed.put("bookings/$booking", mapOf("userId" to alice, "status" to "CONFIRMED", "venueId" to "demo-venue"))
                for (i in 0..30) {
                    seed.put("users/$alice/notifications/item-$i", mapOf(
                        "type" to "BOOKING_CONFIRMED", "title" to "Demo booking $i", "body" to "Saved details",
                        "data" to mapOf("bookingId" to booking), "isRead" to false,
                        "createdAt" to Instant.ofEpochSecond(1_700_000_000L + i),
                    ))
                }
                val first = inbox.loadPage()
                assertEquals((30 downTo 1).map { "item-$it" }, first.items.map { it.id })
                val next = checkNotNull(first.nextCursor)
                val offPage = inbox.loadById("item-0")!!
                assertEquals(listOf(offPage), inbox.loadPage(next).items)
                assertEquals("smashnow://notifications/booking/$booking", routes.resolve(offPage))
                seed.delete("bookings/$booking")
                assertNull(routes.resolve(offPage))
                assertEquals("Saved details", offPage.body)
                inbox.markRead("item-30")
                assertTrue(inbox.loadById("item-30")!!.isRead)
                assertFalse(inbox.loadById("item-0")!!.isRead)
                inbox.markAllRead()
                assertTrue(inbox.loadPage().items.all { it.isRead })
                assertTrue(inbox.loadById("item-0")!!.isRead)
                assertFalse(prefs.load().getValue(NotificationType.PROMOTION))
                prefs.setEnabled(setOf(NotificationType.PROMOTION), true)
                assertTrue(prefs.load().getValue(NotificationType.PROMOTION))
                session.signOut()
                assertTrue(runCatching { inbox.loadPage() }.exceptionOrNull() is IllegalStateException)
                val bob = createPlayer("bob-$runId@example.test")
                seed.put("bookings/$booking", mapOf("userId" to bob, "status" to "CONFIRMED", "venueId" to "demo-venue"))
                assertTrue(inbox.loadPage().items.isEmpty())
                assertNull(inbox.loadById("item-0"))
                assertTrue(runCatching { inbox.loadPage(next) }.exceptionOrNull() is IllegalArgumentException)
                assertFalse(prefs.load().getValue(NotificationType.PROMOTION))
                auth.signInWithEmailAndPassword(aliceEmail, password).await()
                assertNull(routes.resolve(offPage))
                assertTrue(prefs.load().getValue(NotificationType.PROMOTION))
            } finally {
                auth.signOut()
                app.delete()
            }
        }
    }
}

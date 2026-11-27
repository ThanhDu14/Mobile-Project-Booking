package com.example.booking_app.feature.notification

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.DataStoreSessionPrefs
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.notification.data.FirestoreNotificationPreferencesRepository
import com.example.booking_app.feature.notification.data.DataStoreNotificationPreferenceCache
import com.example.booking_app.feature.notification.data.FirestoreNotificationRepository
import com.example.booking_app.feature.notification.data.FirestoreNotificationRouteResolver
import com.example.booking_app.feature.notification.domain.Notification
import com.example.booking_app.feature.notification.domain.NotificationPage
import com.example.booking_app.feature.notification.domain.NotificationType
import com.example.booking_app.testing.EmulatorAdmin
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.time.Instant
import java.util.UUID

/** Run against demo emulators with USB reverse ports 8080 and 9099. */
@HiltAndroidTest
class NotificationEmulatorDeviceTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Test
    fun serverPagingPreferencesRoutingAndAccountSwitch(): Unit = runBlocking {
        withTimeout(150_000) {
            android.util.Log.i("NotificationAcceptance", "Starting emulator SDK journey")
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
            val cacheFile = File(context.cacheDir, "notification-$runId.preferences_pb")
            val cacheScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val store = PreferenceDataStoreFactory.create(scope = cacheScope) { cacheFile }
            val sessionPrefs = DataStoreSessionPrefs(store)
            val session = object : SessionManager {
                override val sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut(null))
                override val isGuest = MutableStateFlow(false)
                override suspend fun refresh(forceTokenRefresh: Boolean) {}
                override suspend fun signOut() {
                    auth.signOut()
                    sessionPrefs.clearOnSignOut()
                }
                override fun currentUid() = auth.currentUser?.uid
                override fun currentAppRole() = auth.currentUser?.let { AppRole.PLAYER }
            }
            val seed = EmulatorAdmin(project, "127.0.0.1:8080")
            val inbox = FirestoreNotificationRepository(session, db)
            val routes = FirestoreNotificationRouteResolver(db, session)
            val cache = DataStoreNotificationPreferenceCache(store, session)
            val prefs = FirestoreNotificationPreferencesRepository(session, db, cache)
            val password = "Notification-Test-Only"
            val aliceEmail = "alice-$runId@example.test"
            val instrumentThread = Thread.currentThread()
            val watchdog = Thread {
                try {
                    Thread.sleep(30_000)
                    android.util.Log.w("NotificationAcceptance", "Slow device journey\n" +
                        instrumentThread.stackTrace.take(28).joinToString("\n"))
                } catch (_: InterruptedException) {
                    // The journey finished before the diagnostic timer.
                }
            }.apply { isDaemon = true; start() }
            suspend fun createUser(email: String, role: String = "PLAYER"): String {
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
                        .put("customAttributes", JSONObject().put("appRole", role).toString())
                    connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                    check(connection.responseCode == 200) { "Failed to set demo account claim" }
                } finally {
                    connection.disconnect()
                }
                assertEquals(role, auth.currentUser!!.getIdToken(true).await().claims["appRole"])
                seed.put("users/$uid", mapOf("role" to role, "accountStatus" to "ACTIVE") +
                    if (role == "OWNER") mapOf("ownerStatus" to "APPROVED") else emptyMap())
                return uid
            }
            // Keep the test package in the foreground during the SDK journey on physical devices.
            hilt.inject()
            NotificationUiFixture.reset()
            NotificationUiFixture.response.complete(NotificationPage(emptyList(), null))
            val activity = ActivityScenario.launch(NotificationTestActivity::class.java)
            try {
                val phase = InstrumentationRegistry.getArguments().getString("notificationProducerPhase")
                val bobEmail = "bob-$runId@example.test"
                val alice = createUser(aliceEmail)
                android.util.Log.i("NotificationAcceptance", "Demo SDK account and profile ready")
                val bob: String
                if (phase == "payment" || phase == "group") {
                    session.signOut()
                    bob = createUser(bobEmail)
                    if (phase == "group") auth.signInWithEmailAndPassword(aliceEmail, password).await()
                } else {
                    val booking = "notification-$runId"
                    seed.put("bookings/$booking", mapOf("userId" to alice, "status" to "CONFIRMED", "venueId" to "demo-venue"))
                    for (i in 0..64) {
                        seed.put("users/$alice/notifications/item-$i", mapOf(
                            "type" to "BOOKING_CONFIRMED", "title" to "Demo booking $i", "body" to "Saved details",
                            "data" to mapOf("bookingId" to booking), "isRead" to false,
                            "createdAt" to Instant.ofEpochSecond(1_700_000_000L + i),
                        ))
                    }
                    android.util.Log.i("NotificationAcceptance", "Core seeded 65 inbox rows")
                    val first = inbox.loadPage()
                    assertEquals((64 downTo 35).map { "item-$it" }, first.items.map { it.id })
                    val next = checkNotNull(first.nextCursor)
                    val second = inbox.loadPage(next)
                    assertEquals((34 downTo 5).map { "item-$it" }, second.items.map { it.id })
                    val third = inbox.loadPage(checkNotNull(second.nextCursor))
                    assertEquals((4 downTo 0).map { "item-$it" }, third.items.map { it.id })
                    assertNull(third.nextCursor)
                    val offPage = inbox.loadById("item-0")!!
                    assertEquals(offPage, third.items.last())
                    assertEquals("smashnow://notifications/booking/$booking", routes.resolve(offPage))
                    assertCatalogRoutes(seed, routes, booking, alice)
                    android.util.Log.i("NotificationAcceptance", "Core checked source and saved-detail routes")
                    seed.delete("bookings/$booking")
                    assertNull(routes.resolve(offPage))
                    assertEquals("Saved details", offPage.body)
                    inbox.markRead("item-30")
                    assertTrue(inbox.loadById("item-30")!!.isRead)
                    assertFalse(inbox.loadById("item-0")!!.isRead)
                    inbox.markAllRead()
                    val firstRead = inbox.loadPage()
                    assertTrue(firstRead.items.all { it.isRead })
                    val secondRead = inbox.loadPage(checkNotNull(firstRead.nextCursor))
                    assertTrue(secondRead.items.all { it.isRead })
                    assertTrue(inbox.loadPage(checkNotNull(secondRead.nextCursor)).items.all { it.isRead })
                    assertTrue(inbox.loadById("item-0")!!.isRead)
                    assertFalse(prefs.load().getValue(NotificationType.PROMOTION))
                    assertTrue(prefs.load().getValue(NotificationType.BOOKING_NEW))
                    assertTrue(runCatching { prefs.setEnabled(setOf(NotificationType.BOOKING_NEW), false) }.isFailure)
                    prefs.setEnabled(setOf(NotificationType.PROMOTION), true)
                    assertTrue(prefs.load().getValue(NotificationType.PROMOTION))
                    android.util.Log.i("NotificationAcceptance", "Core server state saved, disabling SDK network")
                    db.disableNetwork().await()
                    try {
                        val offline = prefs.loadSnapshot()
                        assertTrue(offline.fromCache)
                        assertTrue(offline.values.getValue(NotificationType.PROMOTION))
                        android.util.Log.i("NotificationAcceptance", "Core loaded read-only preference cache while offline")
                        assertTrue(runCatching { prefs.setEnabled(setOf(NotificationType.PROMOTION), false) }.isFailure)
                        assertTrue(runCatching { inbox.loadPage() }.isFailure)
                    } finally {
                        db.enableNetwork().await()
                    }
                    val online = prefs.loadSnapshot()
                    assertFalse(online.fromCache)
                    assertTrue(online.values.getValue(NotificationType.PROMOTION))
                    android.util.Log.i("NotificationAcceptance", "Core loaded server settings after reconnect")
                    assertTrue(inbox.loadPage().items.all { it.isRead })
                    session.signOut()
                    assertTrue(runCatching { inbox.loadPage() }.exceptionOrNull() is IllegalStateException)
                    bob = createUser(bobEmail)
                    seed.put("bookings/$booking", mapOf("userId" to bob, "status" to "CONFIRMED", "venueId" to "demo-venue"))
                    assertTrue(inbox.loadPage().items.isEmpty())
                    assertNull(inbox.loadById("item-0"))
                    assertTrue(runCatching { inbox.loadPage(next) }.exceptionOrNull() is IllegalArgumentException)
                    assertFalse(prefs.load().getValue(NotificationType.PROMOTION))
                    auth.signInWithEmailAndPassword(aliceEmail, password).await()
                    assertNull(cache.read(alice, cache.epoch()))
                    assertNull(routes.resolve(offPage))
                    assertTrue(prefs.load().getValue(NotificationType.PROMOTION))
                    android.util.Log.i("NotificationAcceptance", "Core SDK: paging, reads, offline cache, reconnect, source and account isolation")
                }
                InstrumentationRegistry.getArguments().getString("notificationProducerSecret")?.let { secret ->
                    val joined = Instant.now().minusSeconds(60)
                    if (phase != "payment") {
                        val group = UUID.randomUUID().toString()
                        val chat = "group_$group"
                        val message = UUID.randomUUID().toString()
                        val post = UUID.randomUUID().toString()
                        seed.put("groups/$group", mapOf("ownerId" to alice, "status" to "ACTIVE", "visibility" to "PRIVATE", "memberCount" to 2))
                        for ((uid, role) in listOf(alice to "OWNER", bob to "MEMBER")) {
                            seed.put("groups/$group/members/$uid", mapOf("role" to role, "joinedAt" to joined))
                        }
                        seed.put("chats/$chat", mapOf(
                            "type" to "GROUP", "status" to "ACTIVE", "groupId" to group,
                            "memberIds" to listOf(alice, bob), "unreadCounts" to mapOf(alice to 0, bob to 0),
                        ))
                        seed.put("chats/$chat/participants/$bob", mapOf("muted" to true, "userId" to bob))
                        val token = auth.currentUser!!.getIdToken(false).await().token!!
                        val send = JSONObject().put("chatId", chat).put("messageId", message).put("text", "F1 demo")
                        assertEquals(201, callProducer("chat-send", secret, token, send))
                        assertEquals(200, callProducer("chat-send", secret, token, send))
                        val event = JSONObject().put("action", "create").put("groupId", group).put("requestId", post)
                            .put("type", "EVENT").put("eventTitle", "F1 demo").put("eventLocation", "Sân demo")
                            .put("eventAt", Instant.now().plusSeconds(3600).toString()).put("expectedCount", 4)
                        assertEquals(201, callProducer("group-post", secret, token, event))
                        assertEquals(200, callProducer("notification-delivery?group=$group", secret, token, JSONObject()))
                        assertFalse(inbox.loadPage().items.any { it.data["chatId"] == chat || it.data["groupId"] == group })

                        session.signOut()
                        assertNull(session.currentUid())
                        auth.signInWithEmailAndPassword(bobEmail, password).await()
                        android.util.Log.i("NotificationAcceptance", "F1 receiver signed in")
                        val items = inbox.loadPage().items
                        val chatItem = items.single { it.type == "CHAT_MESSAGE" && it.data["chatId"] == chat }
                        val eventItem = items.single { it.type == "GROUP_ACTIVITY" && it.data["groupId"] == group }
                        assertEquals(message, chatItem.data["messageId"])
                        assertEquals("EVENT_CREATED", eventItem.data["event"])
                        assertEquals(post, eventItem.data["postId"])
                        android.util.Log.i("NotificationAcceptance", "F1 receiver found both source notifications")
                        assertEquals("smashnow://chat/$chat", routes.resolve(chatItem))
                        assertEquals("smashnow://community/group/$group", routes.resolve(eventItem))
                        android.util.Log.i("NotificationAcceptance", "F1 routes resolved before permission change")
                        val removal = JSONObject().put("action", "remove").put("groupId", group)
                            .put("userId", bob).put("requestId", UUID.randomUUID().toString())
                        assertEquals(200, callProducer("group-manage", secret, token, removal))
                        android.util.Log.i("NotificationAcceptance", "F1 producer removed receiver from the group and chat")
                        assertNull(routes.resolve(chatItem))
                        assertNull(routes.resolve(eventItem))
                        assertTrue(inbox.loadById(chatItem.id)!!.body.isNotBlank())
                        assertTrue(inbox.loadById(eventItem.id)!!.body.isNotBlank())
                        session.signOut()
                        auth.signInWithEmailAndPassword(aliceEmail, password).await()
                        assertNull(inbox.loadById(chatItem.id))
                        assertNull(inbox.loadById(eventItem.id))
                        android.util.Log.i("NotificationAcceptance", "F1 real SDK: producer, replay, group/chat routes, lost access and UID isolation")
                    }
                    if (phase == "group") return@let

                    session.signOut()
                    val ownerEmail = "owner-$runId@example.test"
                    android.util.Log.i("NotificationAcceptance", "P2/B1 begin with isolated emulator accounts")
                    val owner = createUser(ownerEmail, "OWNER")
                    val ownerToken = auth.currentUser!!.getIdToken(false).await().token!!
                    val venue = UUID.randomUUID().toString()
                    val code = "P" + venue.replace("-", "").take(12).uppercase()
                    seed.put("venues/$venue", mapOf("status" to "ACTIVE", "ownerId" to owner, "name" to "Sân demo"))
                    seed.put("users/$bob/favorites/$venue", mapOf("venueId" to venue, "createdAt" to joined))
                    val voucher = JSONObject().put("venueId", venue).put("code", code).put("type", "FIXED")
                        .put("value", 10_000).put("maxDiscount", 10_000).put("minOrderTotal", 0)
                        .put("usageLimit", 10).put("perUserLimit", 1).put("isActive", true)
                        .put("validFrom", joined.toString()).put("validTo", Instant.now().plusSeconds(3600).toString())
                    assertEquals(200, callProducer("owner-create-voucher", secret, ownerToken, voucher))
                    assertEquals(200, callProducer("notification-delivery?venue=$venue", secret, ownerToken, JSONObject()))

                    session.signOut()
                    auth.signInWithEmailAndPassword(bobEmail, password).await()
                    val promo = inbox.loadPage().items.single { it.type == "PROMOTION" && it.data["venueId"] == venue }
                    assertEquals(code, promo.data["code"])
                    assertNull(routes.resolve(promo))
                    assertTrue(promo.body.contains(code))
                    val court = UUID.randomUUID().toString()
                    val slot = "${court}_20300102_1800"
                    val hold = UUID.randomUUID().toString()
                    seed.put("venues/$venue/courts/$court", mapOf("name" to "Sân số 1"))
                    seed.put("venues/$venue/priceRules/base", mapOf(
                        "daysOfWeek" to emptyList<String>(), "startTime" to "0600", "endTime" to "2200", "pricePerHour" to 120_000,
                    ))
                    seed.put("slots/$slot", mapOf(
                        "venueId" to venue, "courtId" to court, "status" to "HELD", "holdBy" to bob,
                        "bookingId" to hold, "holdExpiresAt" to Instant.now().plusSeconds(300),
                    ))
                    seed.put("bookings/$hold", mapOf(
                        "venueId" to venue, "userId" to bob, "status" to "HOLD", "items" to listOf(mapOf("slotId" to slot)),
                    ))
                    val checkout = JSONObject().put("requestId", hold).put("venueId", venue)
                        .put("slotIds", org.json.JSONArray(listOf(slot))).put("voucherCode", code).put("expectedTotal", 50_000)
                    val playerToken = auth.currentUser!!.getIdToken(false).await().token!!
                    assertEquals(200, callProducer("booking-payment-confirm", secret, playerToken, checkout))
                    assertEquals(200, callProducer("booking-payment-confirm", secret, playerToken, checkout))
                    assertNull(inbox.loadById("booking_new_$hold"))

                    session.signOut()
                    auth.signInWithEmailAndPassword(ownerEmail, password).await()
                    assertNull(inbox.loadById(promo.id))
                    val newBooking = checkNotNull(inbox.loadById("booking_new_$hold"))
                    assertEquals("BOOKING_NEW", newBooking.type)
                    assertEquals(hold, newBooking.data["bookingId"])
                    assertNull(routes.resolve(newBooking))
                    android.util.Log.i("NotificationAcceptance", "P2 and B1 real SDK: favorite voucher, checkout replay, saved details and UID isolation")
                }
            } catch (failure: Throwable) {
                android.util.Log.e("NotificationAcceptance", "Device journey failed", failure)
                throw failure
            } finally {
                watchdog.interrupt()
                auth.signOut()
                cacheScope.cancel()
                cacheFile.delete()
                app.delete()
                activity.close()
            }
        }
    }

    private suspend fun assertCatalogRoutes(
        seed: EmulatorAdmin,
        routes: FirestoreNotificationRouteResolver,
        booking: String,
        uid: String,
    ) {
        val venue = "$booking-venue"
        val session = "$booking-drop-in"
        val review = "$booking-review"
        seed.put("venues/$venue", mapOf("status" to "ACTIVE"))
        seed.put("dropInSessions/$session", mapOf("status" to "OPEN", "venueId" to venue))
        seed.put("reviews/$review", mapOf("status" to "VISIBLE", "userId" to uid, "venueId" to venue, "hasOwnerReply" to true))
        fun item(type: NotificationType, vararg refs: Pair<String, String>) =
            Notification(type.name, type.name, "Saved route title", "Saved route body", refs.toMap(), false, 1)
        for (type in listOf(NotificationType.BOOKING_REJECTED, NotificationType.BOOKING_CANCELLED, NotificationType.BOOKING_REMINDER)) {
            assertEquals(type.name, "smashnow://notifications/booking/$booking", routes.resolve(item(type, "bookingId" to booking)))
        }
        val reminder = item(NotificationType.REVIEW_REMINDER, "bookingId" to booking)
        assertEquals("smashnow://review/write/$booking", routes.resolve(reminder))
        val dropIn = listOf(NotificationType.DROPIN_REGISTERED, NotificationType.DROPIN_SPOT_OPENED,
            NotificationType.DROPIN_CHANGED, NotificationType.DROPIN_WAITLIST_CLOSED)
            .map { item(it, "sessionId" to session) }
        for (notification in dropIn) {
            assertEquals(notification.type, "smashnow://dropin/session/$session", routes.resolve(notification))
        }
        val reply = item(NotificationType.REVIEW_REPLIED, "reviewId" to review, "venueId" to venue)
        assertEquals("smashnow://review/venue/$venue", routes.resolve(reply))
        for (type in listOf(NotificationType.BOOKING_NEW, NotificationType.REVIEW_NEW, NotificationType.PROMOTION,
            NotificationType.OWNER_APPROVED, NotificationType.OWNER_REJECTED, NotificationType.OWNER_REVOKED, NotificationType.SYSTEM)) {
            assertNull(type.name, routes.resolve(item(type, "bookingId" to booking, "venueId" to venue, "reviewId" to review)))
        }
        seed.delete("dropInSessions/$session")
        for (notification in dropIn) assertNull(notification.type, routes.resolve(notification))
        seed.delete("reviews/$review")
        assertNull(routes.resolve(reply))
    }

    private fun callProducer(name: String, key: String, token: String, body: JSONObject): Int {
        val connection = URL("http://127.0.0.1:18765/functions/v1/$name").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Connection", "close")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("x-cron-secret", key)
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            connection.responseCode.also { code ->
                if (code in 200..299) connection.inputStream.use { it.readBytes() }
            }
        } finally {
            connection.disconnect()
        }
    }
}

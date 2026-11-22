package com.example.booking_app.feature.notification

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.notification.data.FirestoreNotificationRouteResolver
import com.example.booking_app.feature.notification.domain.Notification
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NotificationRouteTest {
    private val fs = mockk<FirebaseFirestore>()
    private val session = mockk<SessionManager>()
    private val ref = mockk<DocumentReference>()
    private val snapshot = mockk<DocumentSnapshot>()
    private val resolver = FirestoreNotificationRouteResolver(fs, session)
    init {
        every { session.currentUid() } returns "alice"
        every { fs.collection("bookings").document("source") } returns ref
        every { fs.collection("dropInSessions").document("source") } returns ref
        every { fs.collection("chats").document("source") } returns ref
        every { ref.get(Source.SERVER) } returns Tasks.forResult(snapshot)
        every { snapshot.exists() } returns true
        every { snapshot.getString("userId") } returns "alice"
        every { snapshot.get("memberIds") } returns listOf("alice")
    }
    private fun item(type: String, key: String = "bookingId") = Notification("one", type, "Saved title", "Saved body", mapOf(key to "source"), false, 1)
    @Test fun `confirmed booking session and chat sources route after server access check`() = runTest {
        assertEquals("smashnow://notifications/booking/source", resolver.resolve(item("BOOKING_CONFIRMED")))
        assertEquals("smashnow://dropin/session/source", resolver.resolve(item("DROPIN_CHANGED", "sessionId")))
        assertEquals("smashnow://chat/source", resolver.resolve(item("CHAT_MESSAGE", "chatId")))
    }
    @Test fun `missing forbidden or wrong owner source falls back to persisted detail`() = runTest {
        every { snapshot.exists() } returns false
        assertNull(resolver.resolve(item("BOOKING_CONFIRMED")))
        every { snapshot.exists() } returns true
        every { snapshot.getString("userId") } returns "bob"
        assertNull(resolver.resolve(item("BOOKING_CONFIRMED")))
        every { ref.get(Source.SERVER) } returns Tasks.forException(IllegalStateException("permission denied"))
        assertNull(resolver.resolve(item("BOOKING_CONFIRMED")))
    }
    @Test fun `unknown contracts and missing identifier use detail without source fetch`() = runTest {
        assertNull(resolver.resolve(item("OWNER_APPROVED")))
        assertNull(resolver.resolve(item("SYSTEM")))
        assertNull(resolver.resolve(item("CHAT_MESSAGE")))
        verify(exactly = 0) { ref.get(Source.SERVER) }
    }
}

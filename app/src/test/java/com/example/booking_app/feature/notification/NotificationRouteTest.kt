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
        assertEquals("smashnow://review/write/source", resolver.resolve(item("REVIEW_REMINDER")))
    }
    @Test fun `missing forbidden or wrong owner source falls back to persisted detail`() = runTest {
        every { snapshot.exists() } returns false
        assertNull(resolver.resolve(item("BOOKING_CONFIRMED")))
        assertNull(resolver.resolve(item("REVIEW_REMINDER")))
        every { snapshot.exists() } returns true
        every { snapshot.getString("userId") } returns "bob"
        assertNull(resolver.resolve(item("BOOKING_CONFIRMED")))
        every { ref.get(Source.SERVER) } returns Tasks.forException(IllegalStateException("permission denied"))
        assertNull(resolver.resolve(item("BOOKING_CONFIRMED")))
    }
    @Test fun `saved detail types and missing identifiers avoid source fetch`() = runTest {
        val savedTypes = listOf("BOOKING_NEW", "PROMOTION", "REVIEW_NEW", "OWNER_APPROVED",
            "OWNER_REJECTED", "OWNER_REVOKED", "SYSTEM")
        val identifiers = mapOf("bookingId" to "b", "venueId" to "v", "reviewId" to "r",
            "applicationId" to "a", "purpose" to "OWNER_ONBOARDING", "code" to "DEMO")
        for (type in savedTypes) assertNull(resolver.resolve(item(type).copy(data = identifiers)))
        assertNull(resolver.resolve(item("CHAT_MESSAGE")))
        verify(exactly = 0) { ref.get(Source.SERVER) }
    }
    @Test fun `canonical and approved legacy reply names recheck both review and venue`() = runTest {
        every { fs.collection("reviews").document("review") } returns ref
        every { fs.collection("venues").document("venue") } returns ref
        every { snapshot.getString("venueId") } returns "venue"
        every { snapshot.getString("status") } returns "VISIBLE"
        every { snapshot.getBoolean("hasOwnerReply") } returns true
        for (type in listOf("REVIEW_REPLIED", "REVIEW_REPLY")) {
            val item = item(type).copy(data = mapOf("reviewId" to "review", "venueId" to "venue"))
            assertEquals("smashnow://review/venue/venue", resolver.resolve(item))
            every { snapshot.getString("status") } returns "HIDDEN"
            assertNull(resolver.resolve(item))
            every { snapshot.getString("status") } returns "VISIBLE"
            every { snapshot.getString("userId") } returns "bob"
            assertNull(resolver.resolve(item))
            every { snapshot.getString("userId") } returns "alice"
        }
    }
    @Test fun `reply source deleted inaccessible or account changed falls back`() = runTest {
        val reply = item("REVIEW_REPLIED").copy(data = mapOf("reviewId" to "review", "venueId" to "venue"))
        every { fs.collection("reviews").document("review") } returns ref
        every { snapshot.getString("venueId") } returns "venue"
        every { snapshot.getString("status") } returns "VISIBLE"
        every { snapshot.getBoolean("hasOwnerReply") } returns true
        val venueRef = mockk<DocumentReference>()
        every { fs.collection("venues").document("venue") } returns venueRef
        every { venueRef.get(Source.SERVER) } returns Tasks.forException(IllegalStateException("permission denied"))
        assertNull(resolver.resolve(reply))
        every { snapshot.exists() } returns false
        assertNull(resolver.resolve(reply))
        every { snapshot.exists() } returns true
        every { session.currentUid() } returnsMany listOf("alice", "bob")
        assertNull(resolver.resolve(reply))
    }
}

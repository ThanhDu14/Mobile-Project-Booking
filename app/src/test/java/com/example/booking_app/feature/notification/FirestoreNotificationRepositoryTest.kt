package com.example.booking_app.feature.notification

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.notification.data.FirestoreNotificationRepository
import com.example.booking_app.feature.notification.data.FirestoreNotificationPreferencesRepository
import com.example.booking_app.feature.notification.domain.NotificationType
import com.example.booking_app.core.network.AppError
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FirestoreNotificationRepositoryTest {
    private val session = mockk<SessionManager>()
    private val fs = mockk<FirebaseFirestore>()
    private val user = mockk<DocumentReference>()
    private val inbox = mockk<CollectionReference>()
    private val query = mockk<Query>()
    private var uid: String? = "alice"
    init {
        every { session.currentUid() } answers { uid }
        every { fs.collection("users").document("alice") } returns user
        every { user.collection("notifications") } returns inbox
        every { inbox.orderBy("createdAt", Query.Direction.DESCENDING) } returns query
        every { query.limit(30) } returns query
        every { query.startAfter(any<DocumentSnapshot>()) } returns query
    }
    private fun document(id: Int, read: Boolean = false): DocumentSnapshot = mockk {
        every { this@mockk.id } returns id.toString()
        every { getString("type") } returns "SYSTEM"
        every { getString("title") } returns "Title $id"
        every { getString("body") } returns "Body"
        every { this@mockk.get("data") } returns mapOf("bookingId" to "booking")
        every { getBoolean("isRead") } returns read
        every { getTimestamp("createdAt") } returns Timestamp(java.util.Date(id.toLong()))
        every { reference } returns mockk()
    }
    private fun page(docs: List<DocumentSnapshot>): QuerySnapshot = mockk { every { documents } returns docs }

    @Test fun `loads thirty newest documents and uses snapshot cursor for continuation`() = runTest {
        val docs = (60 downTo 31).map { document(it) }
        every { query.get(Source.SERVER) } returnsMany listOf(Tasks.forResult(page(docs)), Tasks.forResult(page(listOf(document(30)))))
        val repo = FirestoreNotificationRepository(session, fs)
        val first = repo.loadPage()
        assertEquals((60 downTo 31).map(Int::toString), first.items.map { it.id })
        assertEquals("booking", first.items.first().data["bookingId"])
        assertEquals(60L, first.items.first().createdAtMillis)
        assertNotNull(first.nextCursor)
        assertNull(repo.loadPage(first.nextCursor).nextCursor)
        verify { inbox.orderBy("createdAt", Query.Direction.DESCENDING); query.limit(30); query.startAfter(docs.last()) }
    }
    @Test fun `mark all traverses multiple pages and changes only unread isRead`() = runTest {
        val first = (60 downTo 31).map { document(it, it == 60) }
        val second = (30 downTo 1).map { document(it) }
        every { query.get(Source.SERVER) } returnsMany listOf(Tasks.forResult(page(first)), Tasks.forResult(page(second)), Tasks.forResult(page(emptyList())))
        val batch = mockk<WriteBatch>()
        every { fs.batch() } returns batch
        every { batch.update(any<DocumentReference>(), "isRead", true) } returns batch
        every { batch.commit() } returns Tasks.forResult(null)
        FirestoreNotificationRepository(session, fs).markAllRead()
        verify(exactly = 59) { batch.update(any<DocumentReference>(), "isRead", true) }
        verify(exactly = 2) { batch.commit() }
        verify { query.startAfter(first.last()); query.startAfter(second.last()) }
    }
    @Test fun `mark one changes only read flag and rejects invalid identifiers`() = runTest {
        val ref = mockk<DocumentReference>()
        every { inbox.document("one") } returns ref
        every { ref.update("isRead", true) } returns Tasks.forResult(null)
        val repo = FirestoreNotificationRepository(session, fs)
        repo.markRead("one")
        verify(exactly = 1) { ref.update("isRead", true) }
        try { repo.markRead("../other"); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun `network errors propagate instead of empty page`() = runTest {
        every { query.get(Source.SERVER) } returns Tasks.forException(FirebaseNetworkException("offline"))
        try { FirestoreNotificationRepository(session, fs).loadPage(); fail() } catch (_: AppError.Network) {}
    }
    @Test fun `account change during load discards result and old cursor cannot be reused`() = runTest {
        val repo = FirestoreNotificationRepository(session, fs)
        every { query.get(Source.SERVER) } returns Tasks.forResult(page((30 downTo 1).map { document(it) }))
        val cursor = repo.loadPage().nextCursor
        uid = "bob"
        try { repo.loadPage(cursor); fail() } catch (_: Exception) {}
        uid = "alice"
        val task = TaskCompletionSource<QuerySnapshot>()
        every { query.get(Source.SERVER) } returns task.task
        val load = async { runCatching { repo.loadPage() } }
        testScheduler.runCurrent()
        uid = null
        task.setResult(page(emptyList()))
        assertTrue(load.await().isFailure)
    }
    @Test fun `account change after page read prevents mark all writes`() = runTest {
        every { query.get(Source.SERVER) } answers { uid = "bob"; Tasks.forResult(page(listOf(document(1)))) }
        try { FirestoreNotificationRepository(session, fs).markAllRead(); fail() } catch (_: IllegalStateException) {}
        verify(exactly = 0) { fs.batch() }
    }
    @Test fun `preferences apply defaults and mandatory booking new overrides stored false`() = runTest {
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.exists() } returns true
        every { snapshot.get("notificationPrefs") } returns mapOf("BOOKING_NEW" to false, "CHAT_MESSAGE" to false)
        every { user.get(Source.SERVER) } returns Tasks.forResult(snapshot)
        val values = FirestoreNotificationPreferencesRepository(session, fs).load()
        assertTrue(values.getValue(NotificationType.BOOKING_NEW))
        assertFalse(values.getValue(NotificationType.PROMOTION))
        assertFalse(values.getValue(NotificationType.CHAT_MESSAGE))
        assertTrue(values.getValue(NotificationType.BOOKING_REMINDER))
    }
    @Test fun `preference writes use individual field paths and reject disabling mandatory type`() = runTest {
        every { user.update(any<Map<String, Any>>()) } returns Tasks.forResult(null)
        val repo = FirestoreNotificationPreferencesRepository(session, fs)
        repo.setEnabled(setOf(NotificationType.CHAT_MESSAGE), false)
        verify { user.update(match<Map<String, Any>> { it.keys == setOf("notificationPrefs.CHAT_MESSAGE", "updatedAt") && it["notificationPrefs.CHAT_MESSAGE"] == false }) }
        try { repo.setEnabled(setOf(NotificationType.BOOKING_NEW), false); fail() } catch (_: IllegalArgumentException) {}
        verify(exactly = 1) { user.update(any<Map<String, Any>>()) }
    }
    @Test fun `preference write errors propagate and switched account result is rejected`() = runTest {
        val repo = FirestoreNotificationPreferencesRepository(session, fs)
        every { user.update(any<Map<String, Any>>()) } returns Tasks.forException(FirebaseNetworkException("offline"))
        try { repo.setEnabled(setOf(NotificationType.PROMOTION), true); fail() } catch (_: AppError.Network) {}
        every { user.get(Source.SERVER) } answers { uid = "bob"; Tasks.forResult(mockk()) }
        try { repo.load(); fail() } catch (_: IllegalStateException) {}
    }
}

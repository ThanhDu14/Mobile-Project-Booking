package com.example.booking_app.feature.chat.data

import app.cash.turbine.test
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** T037: unit test cho [UnreadChatsReaderImpl] (US3). */
class UnreadChatsReaderImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val chatsCollection = mockk<CollectionReference>()
    private val query = mockk<Query>()
    private val registration = mockk<ListenerRegistration>(relaxed = true)
    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.SignedOut(null))
    private val sessionManager = mockk<SessionManager>(relaxed = true) {
        every { sessionState } returns sessionStateFlow
    }

    private fun chatDoc(
        unreadCounts: Map<String, Long>,
        status: String = "ACTIVE",
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.get("status") } returns status
        every { doc.get("unreadCounts") } returns unreadCounts
        return doc
    }

    private fun snapshotOf(vararg docs: DocumentSnapshot): QuerySnapshot = mockk {
        every { documents } returns docs.toList()
    }

    @Test
    fun `dem so cuoc co unreadCounts lon hon 0 cap nhat khi doi va ve 0 khi dang xuat`() = runTest {
        val listenerSlot = slot<EventListener<QuerySnapshot>>()
        every { firestore.collection("chats") } returns chatsCollection
        every { chatsCollection.whereArrayContains("memberIds", "player1") } returns query
        every { query.addSnapshotListener(capture(listenerSlot)) } returns registration

        val reader = UnreadChatsReaderImpl(firestore, sessionManager)

        reader.observeUnreadChatCount().test {
            // Chưa đăng nhập -> 0.
            assertEquals(0, awaitItem())

            // Đăng nhập player1.
            sessionStateFlow.value = SessionState.SignedIn("player1", AppRole.PLAYER, emailVerified = true)
            testScheduler.runCurrent()

            // 2 cuộc có tin chưa đọc (tổng 8 tin nhưng đếm theo số cuộc = 2), 1 cuộc đã đọc, 1 cuộc DELETED bỏ qua.
            listenerSlot.captured.onEvent(
                snapshotOf(
                    chatDoc(mapOf("player1" to 5L, "owner1" to 0L)),
                    chatDoc(mapOf("player1" to 3L, "member2" to 2L)),
                    chatDoc(mapOf("player1" to 0L, "owner2" to 4L)),
                    chatDoc(mapOf("player1" to 9L), status = "DELETED"),
                ),
                null,
            )
            assertEquals(2, awaitItem())

            // Đọc xong 1 cuộc -> cập nhật xuống 1.
            listenerSlot.captured.onEvent(
                snapshotOf(
                    chatDoc(mapOf("player1" to 0L, "owner1" to 0L)),
                    chatDoc(mapOf("player1" to 3L, "member2" to 2L)),
                    chatDoc(mapOf("player1" to 0L, "owner2" to 4L)),
                ),
                null,
            )
            assertEquals(1, awaitItem())

            // Đăng xuất -> gỡ listener và về 0.
            sessionStateFlow.value = SessionState.SignedOut(null)
            assertEquals(0, awaitItem())
            verify(exactly = 1) { registration.remove() }

            cancelAndIgnoreRemainingEvents()
        }
    }
}

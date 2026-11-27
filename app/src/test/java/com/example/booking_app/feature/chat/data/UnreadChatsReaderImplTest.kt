package com.example.booking_app.feature.chat.data

import app.cash.turbine.test
import com.example.booking_app.core.session.AppMode
import com.example.booking_app.core.session.AppModeController
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

    @Test
    fun `account replacement clears the badge before the next snapshot and removes the old listener`() = runTest {
        val listeners = mutableListOf<EventListener<QuerySnapshot>>()
        every { firestore.collection("chats") } returns chatsCollection
        every { chatsCollection.whereArrayContains("memberIds", any()) } returns query
        every { query.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } answers {
            listeners.add(firstArg())
            registration
        }
        sessionStateFlow.value = SessionState.SignedIn("first", AppRole.PLAYER, true)
        val reader = UnreadChatsReaderImpl(firestore, sessionManager, appModeController)
        reader.observeUnreadChatCount().test {
            testScheduler.runCurrent()
            listeners.first().onEvent(snapshotOf(chatDoc(mapOf("first" to 3L), ownerId = "venue-owner")), null)
            // The first emission must be neutral while this account's snapshot is loading.
            assertEquals(0, awaitItem())
            assertEquals(1, awaitItem())
            sessionStateFlow.value = SessionState.SignedIn("second", AppRole.PLAYER, true)
            testScheduler.runCurrent()
            assertEquals(0, awaitItem())
            verify(exactly = 1) { registration.remove() }
            listeners.first().onEvent(snapshotOf(chatDoc(mapOf("first" to 20L), ownerId = "venue-owner")), null)
            testScheduler.runCurrent()
            expectNoEvents()
            listeners.last().onEvent(snapshotOf(chatDoc(mapOf("second" to 1L), ownerId = "another-owner")), null)
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val firestore = mockk<FirebaseFirestore>()
    private val chatsCollection = mockk<CollectionReference>()
    private val query = mockk<Query>()
    private val registration = mockk<ListenerRegistration>(relaxed = true)
    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.SignedOut(null))
    private val modeFlow = MutableStateFlow(AppMode.PLAYER)
    private val sessionManager = mockk<SessionManager>(relaxed = true) {
        every { sessionState } returns sessionStateFlow
    }
    private val appModeController = mockk<AppModeController>(relaxed = true) {
        every { mode } returns modeFlow
    }

    private fun chatDoc(
        unreadCounts: Map<String, Long>,
        status: String = "ACTIVE",
        type: String = "DIRECT",
        ownerId: String? = null,
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.get("status") } returns status
        every { doc.get("type") } returns type
        every { doc.get("ownerId") } returns ownerId
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

        val reader = UnreadChatsReaderImpl(firestore, sessionManager, appModeController)

        reader.observeUnreadChatCount().test {
            // Chưa đăng nhập -> 0.
            assertEquals(0, awaitItem())

            // Đăng nhập player1.
            sessionStateFlow.value = SessionState.SignedIn("player1", AppRole.PLAYER, emailVerified = true)
            testScheduler.runCurrent()

            // 2 cuộc có tin chưa đọc (tổng 8 tin nhưng đếm theo số cuộc = 2), 1 cuộc đã đọc, 1 cuộc DELETED bỏ qua.
            listenerSlot.captured.onEvent(
                snapshotOf(
                    chatDoc(mapOf("player1" to 5L, "owner1" to 0L), type = "DIRECT", ownerId = "owner1"),
                    chatDoc(mapOf("player1" to 3L, "member2" to 2L), type = "GROUP", ownerId = null),
                    chatDoc(mapOf("player1" to 0L, "owner2" to 4L), type = "DIRECT", ownerId = "owner2"),
                    chatDoc(mapOf("player1" to 9L), status = "DELETED", type = "DIRECT", ownerId = "owner3"),
                ),
                null,
            )
            assertEquals(2, awaitItem())

            // Đọc xong 1 cuộc -> cập nhật xuống 1.
            listenerSlot.captured.onEvent(
                snapshotOf(
                    chatDoc(mapOf("player1" to 0L, "owner1" to 0L), type = "DIRECT", ownerId = "owner1"),
                    chatDoc(mapOf("player1" to 3L, "member2" to 2L), type = "GROUP", ownerId = null),
                    chatDoc(mapOf("player1" to 0L, "owner2" to 4L), type = "DIRECT", ownerId = "owner2"),
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

    @Test
    fun `loc so cuoc chua doc theo che do PLAYER va OWNER`() = runTest {
        val listenerSlot = slot<EventListener<QuerySnapshot>>()
        every { firestore.collection("chats") } returns chatsCollection
        every { chatsCollection.whereArrayContains("memberIds", "owner1") } returns query
        every { query.addSnapshotListener(capture(listenerSlot)) } returns registration

        modeFlow.value = AppMode.PLAYER
        val reader = UnreadChatsReaderImpl(firestore, sessionManager, appModeController)

        reader.observeUnreadChatCount().test {
            assertEquals(0, awaitItem())

            sessionStateFlow.value = SessionState.SignedIn("owner1", AppRole.OWNER, emailVerified = true)
            testScheduler.runCurrent()

            // 1 chat nhóm CLB chưa đọc + 1 chat riêng hỏi chủ sân khác chưa đọc + 2 chat khách hỏi sân của owner1 chưa đọc.
            listenerSlot.captured.onEvent(
                snapshotOf(
                    chatDoc(mapOf("owner1" to 4L), type = "GROUP", ownerId = null),
                    chatDoc(mapOf("owner1" to 2L), type = "DIRECT", ownerId = "owner2"),
                    chatDoc(mapOf("owner1" to 1L), type = "DIRECT", ownerId = "owner1"),
                    chatDoc(mapOf("owner1" to 3L), type = "DIRECT", ownerId = "owner1"),
                ),
                null,
            )
            // Chế độ PLAYER: bỏ 2 chat có ownerId == "owner1" -> còn 2.
            assertEquals(2, awaitItem())

            // Chuyển sang chế độ OWNER: chỉ tính DIRECT có ownerId == "owner1" -> 2 (chỉ đổi giá trị nếu khác; ta cho 1 cuộc của owner1 = 0 trước).
            listenerSlot.captured.onEvent(
                snapshotOf(
                    chatDoc(mapOf("owner1" to 4L), type = "GROUP", ownerId = null),
                    chatDoc(mapOf("owner1" to 2L), type = "DIRECT", ownerId = "owner2"),
                    chatDoc(mapOf("owner1" to 1L), type = "DIRECT", ownerId = "owner1"),
                    chatDoc(mapOf("owner1" to 0L), type = "DIRECT", ownerId = "owner1"),
                ),
                null,
            )
            modeFlow.value = AppMode.OWNER
            assertEquals(1, awaitItem())

            // Quay lại chế độ PLAYER -> về lại 2 (không tạo lại listener Firestore).
            modeFlow.value = AppMode.PLAYER
            assertEquals(2, awaitItem())
            verify(exactly = 1) { query.addSnapshotListener(any()) }

            cancelAndIgnoreRemainingEvents()
        }
    }
}

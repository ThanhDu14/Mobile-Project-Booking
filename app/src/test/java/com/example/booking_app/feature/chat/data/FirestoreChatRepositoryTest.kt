package com.example.booking_app.feature.chat.data

import app.cash.turbine.test
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.model.ChatMode
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Unit test cho [FirestoreChatRepository] (review Argon US1 #1 và US2 #2). */
class FirestoreChatRepositoryTest {

    private class FakeClock(var currentMs: Long = 1_700_000_000_000L) : AppClock {
        override fun now(): Long = currentMs
        override fun elapsedRealtime(): Long = currentMs
    }

    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val actionApi = mockk<ChatActionApi>(relaxed = true)
    private val outbox = mockk<ChatOutbox>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val clock = FakeClock()

    private fun createRepo() = FirestoreChatRepository(
        firestore = firestore,
        actionApi = actionApi,
        outbox = outbox,
        sessionManager = sessionManager,
        clock = clock,
    )

    @Test
    fun `observeChat khong ghi de serverOffsetMs tu lastReadAt cu ma chi cap nhat sau markRead`() = runTest {
        every { sessionManager.currentUid() } returns "player1"

        val chatsCol = mockk<CollectionReference>()
        val chatDocRef = mockk<DocumentReference>()
        val partsCol = mockk<CollectionReference>()
        val partDocRef = mockk<DocumentReference>()
        val chatListenerSlot = slot<EventListener<DocumentSnapshot>>()
        val partListenerSlot = slot<EventListener<DocumentSnapshot>>()
        val reg1 = mockk<ListenerRegistration>(relaxed = true)
        val reg2 = mockk<ListenerRegistration>(relaxed = true)

        every { firestore.collection("chats") } returns chatsCol
        every { chatsCol.document("owner1_player1") } returns chatDocRef
        every { chatDocRef.collection("participants") } returns partsCol
        every { partsCol.document("player1") } returns partDocRef
        every { chatDocRef.addSnapshotListener(capture(chatListenerSlot)) } returns reg1
        every { partDocRef.addSnapshotListener(capture(partListenerSlot)) } returns reg2

        val repo = createRepo()

        repo.observeChat("owner1_player1").test {
            val chatSnap = mockk<DocumentSnapshot> {
                every { exists() } returns true
                every { id } returns "owner1_player1"
                every { data } returns mapOf(
                    "type" to "DIRECT",
                    "memberIds" to listOf("owner1", "player1"),
                    "ownerId" to "owner1",
                    "playerId" to "player1",
                    "status" to "ACTIVE",
                )
            }
            val oldLastReadAt = Instant.ofEpochMilli(clock.currentMs - 40L * 60_000L) // 40 phút trước
            val partSnap = mockk<DocumentSnapshot> {
                every { exists() } returns true
                every { id } returns "player1"
                every { metadata } returns mockk<SnapshotMetadata> { every { hasPendingWrites() } returns false }
                every { data } returns mapOf(
                    "muted" to false,
                    "blockedOther" to false,
                    "lastReadAt" to Timestamp(oldLastReadAt.epochSecond, oldLastReadAt.nano),
                )
            }
            chatListenerSlot.captured.onEvent(chatSnap, null)
            partListenerSlot.captured.onEvent(partSnap, null)

            val detail = awaitItem()!!
            assertEquals("owner1_player1", detail.id)
            // KHÔNG được ghi đè serverOffsetMs thành -40 phút từ lastReadAt cũ!
            assertEquals(0L, repo.estimatedServerOffsetMs())

            cancelAndIgnoreRemainingEvents()
        }

        // Khi markRead chạy và đọc lại serverTimestamp vừa ghi (+5 giây so với local): cập nhật đúng +5000ms.
        val batch = mockk<WriteBatch>(relaxed = true)
        every { firestore.batch() } returns batch
        every { batch.commit() } returns Tasks.forResult(null)
        val serverNow = Instant.ofEpochMilli(clock.currentMs + 5_000L)
        val freshPartDoc = mockk<DocumentSnapshot> {
            every { getTimestamp("lastReadAt") } returns Timestamp(serverNow.epochSecond, serverNow.nano)
        }
        every { partDocRef.get() } returns Tasks.forResult(freshPartDoc)

        val res = repo.markRead("owner1_player1")
        assertEquals(true, res.isSuccess)
        assertEquals(5_000L, repo.estimatedServerOffsetMs())
    }

    @Test
    fun `observeChats loai bo nhom da xoa va khong tao lai groupListeners khi groupIds khong doi`() = runTest {
        every { sessionManager.currentUid() } returns "player1"

        val chatsCol = mockk<CollectionReference>()
        val chatsQuery1 = mockk<Query>()
        val chatsQuery2 = mockk<Query>()
        val chatsListenerSlot = slot<EventListener<QuerySnapshot>>()
        val chatsReg = mockk<ListenerRegistration>(relaxed = true)

        val groupsCol = mockk<CollectionReference>()
        val groupsQuery1 = mockk<Query>()
        val groupsQuery2 = mockk<Query>()
        val groupsListenerSlot = slot<EventListener<QuerySnapshot>>()
        val groupsReg = mockk<ListenerRegistration>(relaxed = true)

        val participantsGroupQuery = mockk<Query>()
        val participantsQuery = mockk<Query>()
        val participantsListenerSlot = slot<EventListener<QuerySnapshot>>()
        val participantsReg = mockk<ListenerRegistration>(relaxed = true)

        every { firestore.collection("chats") } returns chatsCol
        every { chatsCol.whereArrayContains("memberIds", "player1") } returns chatsQuery1
        every { chatsQuery1.orderBy("updatedAt", Query.Direction.DESCENDING) } returns chatsQuery2
        every { chatsQuery2.addSnapshotListener(capture(chatsListenerSlot)) } returns chatsReg

        every { firestore.collection("groups") } returns groupsCol
        every { groupsCol.whereIn(FieldPath.documentId(), any()) } returns groupsQuery1
        every { groupsQuery1.whereIn("status", listOf("ACTIVE", "LOCKED")) } returns groupsQuery2
        every { groupsQuery2.addSnapshotListener(capture(groupsListenerSlot)) } returns groupsReg

        every { firestore.collectionGroup("participants") } returns participantsGroupQuery
        every { participantsGroupQuery.whereEqualTo("userId", "player1") } returns participantsQuery
        every { participantsQuery.addSnapshotListener(capture(participantsListenerSlot)) } returns participantsReg

        val repo = createRepo()

        fun doc(id: String, dataMap: Map<String, Any?>): DocumentSnapshot = mockk {
            every { this@mockk.id } returns id
            every { data } returns dataMap
        }
        fun qSnap(vararg docs: DocumentSnapshot): QuerySnapshot = mockk {
            every { documents } returns docs.toList()
        }

        repo.observeChats(ChatMode.PLAYER).test {
            participantsListenerSlot.captured.onEvent(qSnap(), null)

            // 2 chat nhóm: g-alive (ACTIVE) và g-deleted (không trả về từ query status in [ACTIVE, LOCKED]).
            val chatAlive = doc(
                "group_g-alive",
                mapOf(
                    "type" to "GROUP",
                    "groupId" to "g-alive",
                    "memberIds" to listOf("player1"),
                    "status" to "ACTIVE",
                ),
            )
            val chatDeleted = doc(
                "group_g-deleted",
                mapOf(
                    "type" to "GROUP",
                    "groupId" to "g-deleted",
                    "memberIds" to listOf("player1"),
                    "status" to "ACTIVE",
                ),
            )
            chatsListenerSlot.captured.onEvent(qSnap(chatAlive, chatDeleted), null)

            // Chỉ có g-alive thuộc [ACTIVE, LOCKED] → g-deleted phải bị loại bỏ.
            val groupAliveDoc = doc("g-alive", mapOf("name" to "CLB Đang Hoạt Động", "status" to "ACTIVE", "memberCount" to 5L))
            groupsListenerSlot.captured.onEvent(qSnap(groupAliveDoc), null)

            val firstList = awaitItem()
            assertEquals(listOf("group_g-alive"), firstList.map { it.id })
            assertEquals("CLB Đang Hoạt Động", firstList.single().title)

            // Phát snapshot mới trên chats (đổi unreadCounts) với CÙNG danh sách groupIds → KHÔNG tạo lại groupListeners.
            val chatAliveUpdated = doc(
                "group_g-alive",
                mapOf(
                    "type" to "GROUP",
                    "groupId" to "g-alive",
                    "memberIds" to listOf("player1"),
                    "unreadCounts" to mapOf("player1" to 3L),
                    "status" to "ACTIVE",
                ),
            )
            chatsListenerSlot.captured.onEvent(qSnap(chatAliveUpdated, chatDeleted), null)

            val secondList = awaitItem()
            assertEquals(listOf("group_g-alive"), secondList.map { it.id })
            assertEquals(3, secondList.single().unreadCount)
            verify(exactly = 1) { groupsQuery2.addSnapshotListener(any()) }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeChats khong phat danh sach thieu chat nhom khi chatsListener ban lan 2 truoc groupListeners`() = runTest {
        every { sessionManager.currentUid() } returns "player1"

        val chatsCol = mockk<CollectionReference>()
        val chatsQuery1 = mockk<Query>()
        val chatsQuery2 = mockk<Query>()
        val chatsListenerSlot = slot<EventListener<QuerySnapshot>>()
        val chatsReg = mockk<ListenerRegistration>(relaxed = true)

        val groupsCol = mockk<CollectionReference>()
        val groupsQuery1 = mockk<Query>()
        val groupsQuery2 = mockk<Query>()
        val groupsListenerSlot = slot<EventListener<QuerySnapshot>>()
        val groupsReg = mockk<ListenerRegistration>(relaxed = true)

        val participantsGroupQuery = mockk<Query>()
        val participantsQuery = mockk<Query>()
        val participantsListenerSlot = slot<EventListener<QuerySnapshot>>()
        val participantsReg = mockk<ListenerRegistration>(relaxed = true)

        every { firestore.collection("chats") } returns chatsCol
        every { chatsCol.whereArrayContains("memberIds", "player1") } returns chatsQuery1
        every { chatsQuery1.orderBy("updatedAt", Query.Direction.DESCENDING) } returns chatsQuery2
        every { chatsQuery2.addSnapshotListener(capture(chatsListenerSlot)) } returns chatsReg

        every { firestore.collection("groups") } returns groupsCol
        every { groupsCol.whereIn(FieldPath.documentId(), any()) } returns groupsQuery1
        every { groupsQuery1.whereIn("status", listOf("ACTIVE", "LOCKED")) } returns groupsQuery2
        every { groupsQuery2.addSnapshotListener(capture(groupsListenerSlot)) } returns groupsReg

        every { firestore.collectionGroup("participants") } returns participantsGroupQuery
        every { participantsGroupQuery.whereEqualTo("userId", "player1") } returns participantsQuery
        every { participantsQuery.addSnapshotListener(capture(participantsListenerSlot)) } returns participantsReg

        val repo = createRepo()

        fun doc(id: String, dataMap: Map<String, Any?>): DocumentSnapshot = mockk {
            every { this@mockk.id } returns id
            every { data } returns dataMap
        }
        fun qSnap(vararg docs: DocumentSnapshot): QuerySnapshot = mockk {
            every { documents } returns docs.toList()
        }

        repo.observeChats(ChatMode.PLAYER).test {
            participantsListenerSlot.captured.onEvent(qSnap(), null)

            val directChatV1 = doc(
                "owner1_player1",
                mapOf(
                    "type" to "DIRECT",
                    "memberIds" to listOf("owner1", "player1"),
                    "ownerId" to "owner1",
                    "playerId" to "player1",
                    "unreadCounts" to mapOf("player1" to 0L),
                    "status" to "ACTIVE",
                ),
            )
            val groupChat = doc(
                "group_g-alive",
                mapOf(
                    "type" to "GROUP",
                    "groupId" to "g-alive",
                    "memberIds" to listOf("player1"),
                    "status" to "ACTIVE",
                ),
            )
            // Lần 1 (ví dụ cache): đăng ký groupListeners nhưng groupListeners CHƯA trả kết quả.
            chatsListenerSlot.captured.onEvent(qSnap(directChatV1, groupChat), null)
            expectNoEvents()

            // Lần 2 (ví dụ server cập nhật unreadCounts chat riêng) bắn TRƯỚC khi groupListeners trả lần đầu:
            // KHÔNG được emit sớm danh sách thiếu chat nhóm.
            val directChatV2 = doc(
                "owner1_player1",
                mapOf(
                    "type" to "DIRECT",
                    "memberIds" to listOf("owner1", "player1"),
                    "ownerId" to "owner1",
                    "playerId" to "player1",
                    "unreadCounts" to mapOf("player1" to 2L),
                    "status" to "ACTIVE",
                ),
            )
            chatsListenerSlot.captured.onEvent(qSnap(directChatV2, groupChat), null)
            expectNoEvents()

            // Khi groupListeners trả snapshot đầu tiên: phát đủ cả chat riêng (unreadCount mới = 2) và chat nhóm.
            val groupDoc = doc("g-alive", mapOf("name" to "CLB Đang Hoạt Động", "status" to "ACTIVE", "memberCount" to 5L))
            groupsListenerSlot.captured.onEvent(qSnap(groupDoc), null)

            val emitted = awaitItem()
            assertEquals(listOf("owner1_player1", "group_g-alive"), emitted.map { it.id })
            assertEquals(2, emitted.first { it.id == "owner1_player1" }.unreadCount)
            assertEquals("CLB Đang Hoạt Động", emitted.first { it.id == "group_g-alive" }.title)
            verify(exactly = 1) { groupsQuery2.addSnapshotListener(any()) }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setBlocked het han timeout khi mat mang tra ChatError Network thay vi treo vo han`() = runTest {
        every { sessionManager.currentUid() } returns "player1"

        val chatsCol = mockk<CollectionReference>()
        val chatDocRef = mockk<DocumentReference>()
        val partsCol = mockk<CollectionReference>()
        val partDocRef = mockk<DocumentReference>()
        val hangingTask = TaskCompletionSource<Void>().task // Không bao giờ hoàn tất khi offline.

        every { firestore.collection("chats") } returns chatsCol
        every { chatsCol.document("owner1_player1") } returns chatDocRef
        every { chatDocRef.collection("participants") } returns partsCol
        every { partsCol.document("player1") } returns partDocRef
        every { partDocRef.update("blockedOther", true) } returns hangingTask

        val repo = createRepo()
        val result = repo.setBlocked("owner1_player1", true)
        assertEquals(true, result.isFailure)
        assertEquals(true, result.exceptionOrNull() is ChatError.Network)
    }
}

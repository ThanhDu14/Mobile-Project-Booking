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

class NotificationGroupRouteTest {
    private val fs = mockk<FirebaseFirestore>()
    private val session = mockk<SessionManager>()
    private val group = mockk<DocumentReference>()
    private val member = mockk<DocumentReference>()
    private val post = mockk<DocumentReference>()
    private val groupData = mockk<DocumentSnapshot>()
    private val memberData = mockk<DocumentSnapshot>()
    private val postData = mockk<DocumentSnapshot>()
    private val resolver = FirestoreNotificationRouteResolver(fs, session)
    init {
        every { session.currentUid() } returns "alice"
        every { fs.collection("groups").document("g") } returns group
        every { group.collection("members").document("alice") } returns member
        every { group.collection("posts").document("p") } returns post
        every { group.get(Source.SERVER) } returns Tasks.forResult(groupData)
        every { groupData.exists() } returns true
        every { groupData.getString("status") } returns "ACTIVE"
        every { member.get(Source.SERVER) } returns Tasks.forResult(memberData)
        every { memberData.exists() } returns true
        every { memberData.getString("role") } returns "ADMIN"
        every { post.get(Source.SERVER) } returns Tasks.forResult(postData)
        every { postData.exists() } returns true
    }
    private fun event(type: String, postId: String? = "p") = Notification("n", "GROUP_ACTIVITY", "Saved", "Details",
        mapOf("groupId" to "g", "event" to type) + (postId?.let { mapOf("postId" to it) } ?: emptyMap()), false, 1)

    @Test fun `join requests require current moderator membership`() = runTest {
        assertEquals("smashnow://community/group/g/requests", resolver.resolve(event("JOIN_REQUESTED")))
        every { memberData.getString("role") } returns "MEMBER"
        assertNull(resolver.resolve(event("JOIN_REQUESTED")))
        every { member.get(Source.SERVER) } returns Tasks.forException(IllegalStateException("denied"))
        assertNull(resolver.resolve(event("JOIN_REQUESTED")))
    }
    @Test fun `membership decisions open accessible group intro without requiring continued membership`() = runTest {
        for (type in listOf("JOIN_APPROVED", "JOIN_REJECTED", "REMOVED")) {
            assertEquals("smashnow://community/group/g", resolver.resolve(event(type)))
        }
        verify(exactly = 0) { member.get(any()) }
        every { groupData.getString("status") } returns "DELETED"
        assertNull(resolver.resolve(event("REMOVED")))
    }
    @Test fun `events recheck source post and reject missing inaccessible or malformed targets`() = runTest {
        for (type in listOf("EVENT_CREATED", "EVENT_UPDATED")) assertEquals("smashnow://community/group/g", resolver.resolve(event(type)))
        every { postData.exists() } returns false
        assertNull(resolver.resolve(event("EVENT_UPDATED")))
        every { post.get(Source.SERVER) } returns Tasks.forException(IllegalStateException("denied"))
        assertNull(resolver.resolve(event("EVENT_CREATED")))
        assertNull(resolver.resolve(event("EVENT_CREATED", null)))
        assertNull(resolver.resolve(event("EVENT_CREATED", "bad/path")))
    }
    @Test fun `deleted or unknown event uses saved details and account changes reject fetched source`() = runTest {
        assertNull(resolver.resolve(event("EVENT_DELETED")))
        assertNull(resolver.resolve(event("FUTURE_EVENT")))
        verify(exactly = 0) { group.get(any()) }
        every { session.currentUid() } returnsMany listOf("alice", "bob")
        assertNull(resolver.resolve(event("JOIN_APPROVED")))
    }
}

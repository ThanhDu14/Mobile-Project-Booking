package com.example.booking_app.feature.community.data

import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.community.data.dto.AttendeeDto
import com.example.booking_app.feature.community.data.dto.GroupPostRequest
import com.example.booking_app.feature.community.data.dto.GroupPostResponse
import com.example.booking_app.feature.community.data.dto.PostDto
import com.example.booking_app.feature.community.data.dto.toCreateRequest
import com.example.booking_app.feature.community.data.dto.toDomain
import com.example.booking_app.feature.community.data.dto.toUpdateRequest
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.AttendResult
import com.example.booking_app.feature.community.domain.model.Attendee
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostDraft
import com.example.booking_app.feature.community.domain.model.PostStatus
import com.example.booking_app.feature.community.domain.model.PostType
import com.example.booking_app.feature.community.domain.model.ReportReason
import com.example.booking_app.feature.community.domain.toCommunityError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bảng tin, bài đăng và sự kiện của spec 090 (T048 + T055). Đọc qua Firestore (phân trang + listener bài mới +
 * người tham gia sự kiện), ghi qua Edge Function `group-post`. US6 (`hide`, `unhide`, `report`) sẽ bổ sung sau.
 */
@Singleton
class FirestorePostRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val api: GroupPostApi,
    private val sessionManager: SessionManager,
) : PostRepository {

    private fun postsCollection(groupId: String) =
        firestore.collection(COLLECTION_GROUPS).document(groupId).collection(COLLECTION_POSTS)

    override suspend fun feed(groupId: String, isMod: Boolean, cursor: PageCursor?): Result<Page<Post>> = try {
        val uid = sessionManager.currentUid()
        val col = postsCollection(groupId)
        val cursorTs = cursor?.let { it.key.toTimestamp() }

        if (isMod) {
            var q: Query = col
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            if (cursorTs != null && cursor != null) q = q.startAfter(cursorTs, cursor.id)
            val docs = q.limit(Page.PAGE_SIZE.toLong()).get().await().documents
            val items = attachAttendance(groupId, uid, docs.mapNotNull { it.toPost() })
            Result.success(Page(items, nextCursor(docs)))
        } else {
            var visibleQ: Query = col
                .whereEqualTo("status", PostStatus.VISIBLE.name)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            if (cursorTs != null && cursor != null) visibleQ = visibleQ.startAfter(cursorTs, cursor.id)
            val visibleDocs = visibleQ.limit(Page.PAGE_SIZE.toLong()).get().await().documents
            val visiblePosts = visibleDocs.mapNotNull { it.toPost() }
            val next = nextCursor(visibleDocs)

            val ownHiddenPosts = if (uid != null) {
                var hiddenQ: Query = col
                    .whereEqualTo("authorId", uid)
                    .whereEqualTo("status", PostStatus.HIDDEN.name)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                if (cursorTs != null && cursor != null) hiddenQ = hiddenQ.startAfter(cursorTs, cursor.id)
                val hiddenDocs = runCatching { hiddenQ.limit(Page.PAGE_SIZE.toLong()).get().await().documents }
                    .getOrElse { emptyList() }
                val rawHidden = hiddenDocs.mapNotNull { it.toPost() }
                if (next != null) {
                    val boundary = Instant.ofEpochMilli(next.key)
                    rawHidden.filter { p -> p.createdAt?.let { !it.isBefore(boundary) } == true }
                } else {
                    rawHidden
                }
            } else {
                emptyList()
            }

            val merged = (visiblePosts + ownHiddenPosts)
                .associateBy { it.id }
                .values
                .sortedWith(POST_ORDER)
            Result.success(Page(attachAttendance(groupId, uid, merged), next))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toPostError())
    }

    override fun observeNewPosts(groupId: String, isMod: Boolean, after: Instant): Flow<List<Post>> {
        val col = postsCollection(groupId)
        val afterTs = after.toTimestamp()
        val uid = sessionManager.currentUid()
        val baseFlow = if (isMod) {
            queryPostsFlow(
                col.whereGreaterThan("createdAt", afterTs).orderBy("createdAt", Query.Direction.DESCENDING),
            )
        } else {
            val visibleFlow = queryPostsFlow(
                col.whereEqualTo("status", PostStatus.VISIBLE.name)
                    .whereGreaterThan("createdAt", afterTs)
                    .orderBy("createdAt", Query.Direction.DESCENDING),
            )
            if (uid == null) {
                visibleFlow
            } else {
                val hiddenFlow = queryPostsFlow(
                    col.whereEqualTo("authorId", uid)
                        .whereEqualTo("status", PostStatus.HIDDEN.name)
                        .whereGreaterThan("createdAt", afterTs)
                        .orderBy("createdAt", Query.Direction.DESCENDING),
                )
                combine(visibleFlow, hiddenFlow) { visible, hidden ->
                    (visible + hidden).associateBy { it.id }.values.sortedWith(POST_ORDER)
                }
            }
        }
        return baseFlow.map { attachAttendance(groupId, uid, it) }
    }

    override suspend fun getPost(groupId: String, postId: String): Result<Post?> = try {
        val doc = postsCollection(groupId).document(postId).get().await()
        val post = if (doc.exists()) doc.toPost() else null
        val enriched = if (post != null) {
            attachAttendance(groupId, sessionManager.currentUid(), listOf(post)).firstOrNull()
        } else {
            null
        }
        Result.success(enriched)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toPostError())
    }

    override suspend fun create(groupId: String, draft: PostDraft): Result<Post> =
        callPost(draft.toCreateRequest(groupId)) { it.post?.toDomain() }

    override suspend fun update(groupId: String, draft: PostDraft): Result<Post> =
        callPost(draft.toUpdateRequest(groupId)) { it.post?.toDomain() }

    override suspend fun delete(groupId: String, postId: String): Result<Unit> =
        callPost(GroupPostRequest.delete(groupId, postId)) { }

    // ---------- US4: tham gia sự kiện ----------

    override suspend fun attend(groupId: String, postId: String): Result<AttendResult> =
        callPost(GroupPostRequest.attend(groupId, postId)) { res ->
            val count = res.attendeeCount ?: return@callPost null
            AttendResult(attendeeCount = count, attending = res.attending ?: true)
        }

    override suspend fun unattend(groupId: String, postId: String): Result<AttendResult> =
        callPost(GroupPostRequest.unattend(groupId, postId)) { res ->
            val count = res.attendeeCount ?: return@callPost null
            AttendResult(attendeeCount = count, attending = res.attending ?: false)
        }

    override fun observeAttendees(groupId: String, postId: String): Flow<List<Attendee>> = callbackFlow {
        val reg = postsCollection(groupId).document(postId).collection(COLLECTION_ATTENDEES)
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                when {
                    error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> close(CommunityError.NotMember())
                    error != null -> close(error.toPostError())
                    else -> trySend(
                        snapshot?.documents.orEmpty().map { AttendeeDto.fromMap(it.id, it.data.orEmpty()).toDomain() },
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    // ---------- US6: ẩn/hiện bài và báo cáo ----------

    override suspend fun hide(groupId: String, postId: String): Result<Unit> =
        callPost(GroupPostRequest.hide(groupId, postId)) { }

    override suspend fun unhide(groupId: String, postId: String): Result<Unit> =
        callPost(GroupPostRequest.unhide(groupId, postId)) { }

    override suspend fun report(groupId: String, postId: String, reason: ReportReason, detail: String?): Result<String> =
        callPost(GroupPostRequest.report(groupId, postId, reason.name, detail?.trim()?.takeIf { it.isNotEmpty() })) { res ->
            res.reportId
        }

    private suspend fun attachAttendance(groupId: String, uid: String?, posts: List<Post>): List<Post> {
        if (uid == null || posts.none { it.type == PostType.EVENT && it.event != null }) return posts
        val col = postsCollection(groupId)
        return coroutineScope {
            posts.map { post ->
                if (post.type != PostType.EVENT || post.event == null) {
                    async { post }
                } else {
                    async {
                        val attending = runCatching {
                            col.document(post.id).collection(COLLECTION_ATTENDEES).document(uid).get().await().exists()
                        }.getOrDefault(false)
                        post.copy(event = post.event.copy(iAmAttending = attending))
                    }
                }
            }.awaitAll()
        }
    }

    protected suspend fun <T> callPost(request: GroupPostRequest, map: (GroupPostResponse) -> T?): Result<T> {
        if (sessionManager.currentUid() == null) return Result.failure(CommunityError.Unauthenticated())
        var attempt = 0
        while (true) {
            try {
                val response = api.invoke(request)
                val mapped = map(response) ?: return Result.failure(CommunityError.Unknown("INVALID_RESPONSE"))
                return Result.success(mapped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.toCommunityError()
                if (error is CommunityError.Busy && attempt < MAX_BUSY_RETRIES) {
                    attempt++
                    delay(BUSY_RETRY_DELAY_MS * attempt)
                    continue
                }
                return Result.failure(error)
            }
        }
    }

    private fun queryPostsFlow(query: Query): Flow<List<Post>> = callbackFlow {
        val reg = query.addSnapshotListener { snapshot, error ->
            when {
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(emptyList())
                error != null -> close(error.toPostError())
                else -> trySend(snapshot?.documents.orEmpty().mapNotNull { it.toPost() })
            }
        }
        awaitClose { reg.remove() }
    }

    private fun DocumentSnapshot.toPost(): Post? = PostDto.fromMap(id, data.orEmpty()).toDomain()

    private fun nextCursor(docs: List<DocumentSnapshot>): PageCursor? {
        if (docs.size < Page.PAGE_SIZE) return null
        val last = docs.lastOrNull() ?: return null
        val createdAt = PostDto.fromMap(last.id, last.data.orEmpty()).createdAt ?: return null
        return PageCursor(key = createdAt.toEpochMilli(), id = last.id)
    }

    private fun Long.toTimestamp(): Timestamp = Instant.ofEpochMilli(this).toTimestamp()
    private fun Instant.toTimestamp(): Timestamp = Timestamp(epochSecond, nano)

    private fun Throwable.toPostError(): CommunityError =
        if (this is FirebaseFirestoreException &&
            (code == FirebaseFirestoreException.Code.UNAVAILABLE || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
        ) {
            CommunityError.Network()
        } else if (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            CommunityError.NotMember()
        } else {
            toAppError().toCommunityError()
        }

    private companion object {
        const val COLLECTION_GROUPS = "groups"
        const val COLLECTION_POSTS = "posts"
        const val COLLECTION_ATTENDEES = "attendees"
        const val MAX_BUSY_RETRIES = 2
        const val BUSY_RETRY_DELAY_MS = 500L

        val POST_ORDER: Comparator<Post> = compareByDescending<Post> { it.createdAt ?: Instant.EPOCH }
            .thenByDescending { it.id }
    }
}

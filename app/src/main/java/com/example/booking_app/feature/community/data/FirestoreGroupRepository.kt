package com.example.booking_app.feature.community.data

import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.community.data.dto.GroupDto
import com.example.booking_app.feature.community.data.dto.GroupManageRequest
import com.example.booking_app.feature.community.data.dto.GroupManageResponse
import com.example.booking_app.feature.community.data.dto.toCreateResult
import com.example.booking_app.feature.community.data.dto.toDomain
import com.example.booking_app.feature.community.data.dto.toUpdateResult
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.AvatarAction
import com.example.booking_app.feature.community.domain.model.CreateGroupResult
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupInput
import com.example.booking_app.feature.community.domain.model.GroupMember
import com.example.booking_app.feature.community.domain.model.GroupMembership
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.model.JoinRequest
import com.example.booking_app.feature.community.domain.model.JoinResult
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.model.UpdateGroupResult
import com.example.booking_app.feature.community.domain.toCommunityError
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nhóm của spec 090 (T025). Đọc qua Firestore (snapshot listener), ghi qua Edge Function `group-manage` với
 * `Result.failure(CommunityError)`.
 *
 * US1 chỉ cài `create`, `update`, `observeGroup`, `observeMembership`, `observeMyGroups`. Các hàm còn lại
 * (`search` US2; `join`, `cancelRequest`, `leave` US2; `approve`, `reject`, `setRole`, `transferOwner`, `remove`,
 * `observeMembers`, `observeJoinRequests` US2/US5; `delete` US5) chưa làm: hàm `suspend` trả
 * `Result.failure(CommunityError.Unknown("NOT_IMPLEMENTED"))`, hàm `observe*` phát danh sách rỗng.
 */
@Singleton
class FirestoreGroupRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val api: GroupManageApi,
    private val sessionManager: SessionManager,
) : GroupRepository {

    // ---------- Đọc ----------

    /**
     * Nghe `groups/{groupId}`. Không có document, `DELETED` hoặc bị Rules từ chối (`PERMISSION_DENIED`, Rules GR1)
     * đều phát `null` để màn hình hiện "Nhóm không còn tồn tại"; lỗi khác đóng flow bằng [CommunityError].
     */
    override fun observeGroup(groupId: String): Flow<Group?> = callbackFlow {
        val registration = firestore.collection(COLLECTION_GROUPS).document(groupId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                    error != null -> close(error.toGroupError())
                    else -> trySend(
                        snapshot?.takeIf { it.exists() }
                            ?.let { GroupDto.fromMap(it.id, it.data.orEmpty()).toDomain() }
                            ?.takeIf { it.status != GroupStatus.DELETED },
                    )
                }
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    /** `members/{uid}` (vai trò) gộp `joinRequests/{uid}` (đang chờ duyệt). Khách hoặc bị Rules từ chối: [GroupMembership.NONE]. */
    override fun observeMembership(groupId: String): Flow<GroupMembership> {
        val uid = sessionManager.currentUid() ?: return flowOf(GroupMembership.NONE)
        val group = firestore.collection(COLLECTION_GROUPS).document(groupId)
        val role: Flow<GroupRole?> = documentFlow(group.collection(COLLECTION_MEMBERS).document(uid)) { data ->
            (data["role"] as? String)?.let { raw -> GroupRole.entries.firstOrNull { it.name == raw } } ?: GroupRole.MEMBER
        }
        val pending: Flow<Boolean> = documentFlow(group.collection(COLLECTION_JOIN_REQUESTS).document(uid)) { true }
            .map { it == true }
        return combine(role, pending) { r, p -> GroupMembership(role = r, pending = p && r == null) }.distinctUntilChanged()
    }

    /** Phát `null` khi document không có hoặc không đọc được (PERMISSION_DENIED). */
    private fun <T : Any> documentFlow(
        ref: com.google.firebase.firestore.DocumentReference,
        transform: (Map<String, Any?>) -> T,
    ): Flow<T?> = callbackFlow {
        val registration = ref.addSnapshotListener { snapshot, error ->
            when {
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                error != null -> close(error.toGroupError())
                else -> trySend(snapshot?.takeIf { it.exists() }?.let { transform(it.data.orEmpty()) })
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * "Nhóm của tôi": collection group `members where userId == uid orderBy joinedAt desc`, rồi nghe `groups` theo lô
     * ≤ 10 ID (`whereIn(documentId)`) kèm `status in [ACTIVE, LOCKED]` vì Rules chỉ cho `list` khi query ràng buộc
     * `status`. Mỗi lô có một listener riêng nên thay đổi của nhóm (số thành viên, khóa) cập nhật ngay; kết quả giữ
     * thứ tự `joinedAt`.
     */
    override fun observeMyGroups(): Flow<List<Group>> {
        val uid = sessionManager.currentUid() ?: return flowOf(emptyList())
        return callbackFlow {
            var groupListeners: List<ListenerRegistration> = emptyList()
            val membersListener = firestore.collectionGroup(COLLECTION_MEMBERS)
                .whereEqualTo("userId", uid)
                .orderBy("joinedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    groupListeners.forEach { it.remove() }
                    groupListeners = emptyList()
                    if (error != null) {
                        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) trySend(emptyList()) else close(error.toGroupError())
                        return@addSnapshotListener
                    }
                    val ids = snapshot?.documents.orEmpty().mapNotNull { it.reference.parent.parent?.id }.distinct()
                    if (ids.isEmpty()) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val chunks = ids.chunked(WHERE_IN_LIMIT)
                    val results = arrayOfNulls<List<Group>>(chunks.size)
                    groupListeners = chunks.mapIndexed { index, chunk ->
                        firestore.collection(COLLECTION_GROUPS)
                            .whereIn(FieldPath.documentId(), chunk)
                            .whereIn("status", VISIBLE_STATUSES)
                            .addSnapshotListener { groupSnapshot, groupError ->
                                if (groupError != null) {
                                    if (groupError.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                        results[index] = emptyList()
                                    } else {
                                        close(groupError.toGroupError())
                                        return@addSnapshotListener
                                    }
                                } else {
                                    results[index] = groupSnapshot?.documents.orEmpty()
                                        .mapNotNull { GroupDto.fromMap(it.id, it.data.orEmpty()).toDomain() }
                                }
                                if (results.all { it != null }) {
                                    val byId = results.flatMap { it.orEmpty() }.associateBy { it.id }
                                    trySend(ids.mapNotNull { byId[it] })
                                }
                            }
                    }
                }
            awaitClose {
                membersListener.remove()
                groupListeners.forEach { it.remove() }
            }
        }.distinctUntilChanged()
    }

    // ---------- Ghi qua group-manage ----------

    override suspend fun create(input: GroupInput, hasAvatar: Boolean, requestId: String): Result<CreateGroupResult> =
        manage(
            GroupManageRequest.create(
                requestId = requestId,
                name = input.name,
                description = input.description,
                districtId = input.districtId,
                visibility = input.visibility.name,
                hasAvatar = hasAvatar,
            ),
        ) { it.toCreateResult() }

    override suspend fun update(
        groupId: String,
        input: GroupInput?,
        avatar: AvatarAction,
        requestId: String,
    ): Result<UpdateGroupResult> = manage(
        GroupManageRequest.update(
            requestId = requestId,
            groupId = groupId,
            name = input?.name,
            description = input?.description,
            districtId = input?.districtId,
            visibility = input?.visibility?.name,
            avatar = avatar.name,
        ),
    ) { it.toUpdateResult() }

    /**
     * Gọi `group-manage`: chưa đăng nhập → `Unauthenticated`; `BUSY` tự gửi lại cùng `requestId` tối đa
     * [MAX_BUSY_RETRIES] lần (server idempotent theo `requestId`). `401` đã được [GroupManageApi] làm mới token.
     */
    private suspend fun <T> manage(request: GroupManageRequest, map: (GroupManageResponse) -> T?): Result<T> {
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

    // ---------- Chưa làm ở US1 ----------

    // TODO US2: tìm nhóm, tham gia, hủy yêu cầu, rời nhóm, duyệt/từ chối. TODO US5: vai trò, chuyển chủ nhóm, mời ra, xóa nhóm.
    override suspend fun search(query: String, districtIds: List<String>, cursor: PageCursor?): Result<Page<Group>> = notImplemented()

    override suspend fun delete(groupId: String, confirmName: String, requestId: String): Result<Unit> = notImplemented()

    override suspend fun join(groupId: String, requestId: String): Result<JoinResult> = notImplemented()

    override suspend fun cancelRequest(groupId: String, requestId: String): Result<Unit> = notImplemented()

    override suspend fun leave(groupId: String, requestId: String): Result<Unit> = notImplemented()

    override suspend fun approve(groupId: String, userId: String, requestId: String): Result<Unit> = notImplemented()

    override suspend fun reject(groupId: String, userId: String, requestId: String): Result<Unit> = notImplemented()

    override suspend fun setRole(groupId: String, userId: String, role: GroupRole, requestId: String): Result<Unit> = notImplemented()

    override suspend fun transferOwner(groupId: String, userId: String, requestId: String): Result<Unit> = notImplemented()

    override suspend fun remove(groupId: String, userId: String, requestId: String): Result<Unit> = notImplemented()

    override fun observeMembers(groupId: String): Flow<List<GroupMember>> = flowOf(emptyList())

    override fun observeJoinRequests(groupId: String): Flow<List<JoinRequest>> = flowOf(emptyList())

    private fun <T> notImplemented(): Result<T> = Result.failure(CommunityError.Unknown("NOT_IMPLEMENTED"))

    /** Mất kết nối Firestore (`UNAVAILABLE`, `DEADLINE_EXCEEDED`) là [CommunityError.Network]. */
    private fun Throwable.toGroupError(): CommunityError =
        if (this is FirebaseFirestoreException &&
            (code == FirebaseFirestoreException.Code.UNAVAILABLE || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
        ) {
            CommunityError.Network()
        } else {
            toAppError().toCommunityError()
        }

    private companion object {
        const val COLLECTION_GROUPS = "groups"
        const val COLLECTION_MEMBERS = "members"
        const val COLLECTION_JOIN_REQUESTS = "joinRequests"
        const val WHERE_IN_LIMIT = 10
        val VISIBLE_STATUSES = listOf(GroupStatus.ACTIVE.name, GroupStatus.LOCKED.name)
        const val MAX_BUSY_RETRIES = 2
        const val BUSY_RETRY_DELAY_MS = 500L
    }
}

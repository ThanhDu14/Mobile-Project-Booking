package com.example.booking_app.feature.community.domain

import com.example.booking_app.feature.community.domain.model.AvatarAction
import com.example.booking_app.feature.community.domain.model.CreateGroupResult
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupInput
import com.example.booking_app.feature.community.domain.model.GroupMember
import com.example.booking_app.feature.community.domain.model.GroupMembership
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.JoinRequest
import com.example.booking_app.feature.community.domain.model.JoinResult
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.model.UpdateGroupResult
import kotlinx.coroutines.flow.Flow

/**
 * Nhóm, thành viên và yêu cầu tham gia (T018). Đọc qua Firestore (Flow/`Result`), mọi thao tác ghi qua Edge
 * Function `group-manage` và trả `Result.failure(CommunityError)` (cùng cách `DropInRegistrationRepository`).
 * Thao tác ghi nhận `requestId` do ViewModel sinh và giữ qua `SavedStateHandle`; gửi lại cùng `requestId` thì server
 * trả kết quả cũ. Các hàm `observe*` lấy UID của người đang đăng nhập trong lớp cài đặt.
 */
interface GroupRepository {
    /** Realtime; `null` khi nhóm không có, đã `DELETED` hoặc bị Rules từ chối (hiện "Nhóm không còn tồn tại"). */
    fun observeGroup(groupId: String): Flow<Group?>

    /** Gộp `members/{uid}` và `joinRequests/{uid}` của người đang đăng nhập; khách thì phát [GroupMembership.NONE]. */
    fun observeMembership(groupId: String): Flow<GroupMembership>

    /** "Nhóm của tôi": nhóm `ACTIVE`/`LOCKED` của mình, `joinedAt` mới nhất trước, realtime. */
    fun observeMyGroups(): Flow<List<Group>>

    /** Tìm nhóm `ACTIVE` theo tên (có thể trống) và danh sách quận (trống = mọi quận), `memberCount` giảm dần, 20 nhóm/trang. */
    suspend fun search(query: String, districtIds: List<String>, cursor: PageCursor?): Result<Page<Group>>

    suspend fun create(input: GroupInput, hasAvatar: Boolean, requestId: String): Result<CreateGroupResult>

    /** [avatar] `CONFIRM` sau khi tải ảnh lên đích do `create`/`update` trả về. Các trường `null` thì không đổi. */
    suspend fun update(groupId: String, input: GroupInput?, avatar: AvatarAction, requestId: String): Result<UpdateGroupResult>

    suspend fun delete(groupId: String, confirmName: String, requestId: String): Result<Unit>

    suspend fun join(groupId: String, requestId: String): Result<JoinResult>

    suspend fun cancelRequest(groupId: String, requestId: String): Result<Unit>

    /** Chủ nhóm rời khi chưa chuyển quyền → `CommunityError.OwnerMustTransfer(soleMember)`. */
    suspend fun leave(groupId: String, requestId: String): Result<Unit>

    suspend fun approve(groupId: String, userId: String, requestId: String): Result<Unit>

    suspend fun reject(groupId: String, userId: String, requestId: String): Result<Unit>

    /** [role] chỉ `ADMIN` hoặc `MEMBER`. */
    suspend fun setRole(groupId: String, userId: String, role: GroupRole, requestId: String): Result<Unit>

    suspend fun transferOwner(groupId: String, userId: String, requestId: String): Result<Unit>

    suspend fun remove(groupId: String, userId: String, requestId: String): Result<Unit>

    /** Chủ nhóm và quản trị viên ở đầu, rồi theo `joinedAt`. */
    fun observeMembers(groupId: String): Flow<List<GroupMember>>

    /** Cũ nhất trước (`createdAt`). */
    fun observeJoinRequests(groupId: String): Flow<List<JoinRequest>>
}

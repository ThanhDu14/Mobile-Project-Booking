package com.example.booking_app.feature.community.domain

import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.model.HiddenBy
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostStatus

/**
 * Quyền của người đang xem trong một nhóm, theo bảng ở data-model mục 8. Chỉ dùng để ẩn/hiện nút;
 * server vẫn kiểm tra lại mọi thao tác.
 *
 * Nhóm `LOCKED` (admin hệ thống khóa): mọi quyền ghi đều `false`, trừ [canLeave].
 *
 * @param myUid UID người xem.
 * @param role vai trò trong nhóm; `null` nếu chưa là thành viên.
 */
class GroupPermissions(
    private val myUid: String,
    private val role: GroupRole?,
    private val groupStatus: GroupStatus,
) {
    private val writable: Boolean get() = groupStatus == GroupStatus.ACTIVE
    private val isMember: Boolean get() = role != null
    private val isOwner: Boolean get() = role == GroupRole.OWNER
    private val isModerator: Boolean get() = role == GroupRole.OWNER || role == GroupRole.ADMIN

    /** Sửa thông tin nhóm: chỉ chủ nhóm. */
    val canEdit: Boolean get() = writable && isOwner

    /** Xóa nhóm: chỉ chủ nhóm (sau khi gõ đúng tên). */
    val canDeleteGroup: Boolean get() = writable && isOwner

    /** Phong/gỡ quản trị viên, chuyển quyền chủ nhóm: chỉ chủ nhóm. */
    val canManageRoles: Boolean get() = writable && isOwner

    /** Duyệt/từ chối yêu cầu, ẩn/hiện bài: chủ nhóm và quản trị viên. */
    val canModerate: Boolean get() = writable && isModerator

    /** Đăng bài, tạo sự kiện: thành viên của nhóm đang hoạt động. */
    val canPost: Boolean get() = writable && isMember

    /** Tham gia/bỏ tham gia sự kiện (việc sự kiện đã diễn ra hay chưa do [EventInfo.isPast] quyết định). */
    val canAttend: Boolean get() = writable && isMember

    /** Rời nhóm: thành viên nào cũng bấm được, kể cả nhóm `LOCKED`; chủ nhóm sẽ được yêu cầu chuyển quyền trước. */
    val canLeave: Boolean get() = isMember

    /** Mời [targetRole] ra khỏi nhóm: chủ nhóm mời bất kỳ ai trừ mình; quản trị viên chỉ mời thành viên thường. */
    fun canRemove(targetUid: String, targetRole: GroupRole): Boolean {
        if (!writable || targetUid == myUid) return false
        return when (role) {
            GroupRole.OWNER -> true
            GroupRole.ADMIN -> targetRole == GroupRole.MEMBER
            else -> false
        }
    }

    /** Ẩn bài đang hiển thị. */
    fun canHide(post: Post): Boolean = canModerate && post.status == PostStatus.VISIBLE

    /** Hiện lại bài: chỉ khi nhóm là bên ẩn (`hiddenBy == GROUP`); bài admin hệ thống ẩn thì không. */
    fun canUnhide(post: Post): Boolean =
        canModerate && post.status == PostStatus.HIDDEN && post.hiddenBy == HiddenBy.GROUP

    /** Báo cáo: thành viên xem bài đang hiển thị không phải của mình. */
    fun canReport(post: Post): Boolean =
        writable && isMember && post.status == PostStatus.VISIBLE && post.author.userId != myUid

    /** Sửa bài: chỉ người đăng. */
    fun canEditPost(post: Post): Boolean = writable && isMember && post.author.userId == myUid

    /** Xóa bài: chỉ người đăng. */
    fun canDeletePost(post: Post): Boolean = canEditPost(post)
}

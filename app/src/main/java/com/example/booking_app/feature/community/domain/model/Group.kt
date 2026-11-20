package com.example.booking_app.feature.community.domain.model

import java.time.Instant

/** Nhóm/CLB (data-model mục 1). */
data class Group(
    val id: String,
    val name: String,
    val description: String,
    /** Path trong bucket `group-avatars`; `null` thì dùng ảnh mặc định. */
    val avatarPath: String?,
    val districtId: String,
    val ownerId: String,
    val visibility: GroupVisibility,
    val memberCount: Int,
    val pendingRequestCount: Int,
    val status: GroupStatus,
) {
    val isLocked: Boolean get() = status == GroupStatus.LOCKED
}

/**
 * Quan hệ của người đang xem với nhóm: [role] `null` = chưa là thành viên; [pending] = đã xin và đang chờ duyệt.
 * Quyết định nút "Tham gia" / "Xin tham gia" / "Đang chờ duyệt".
 */
data class GroupMembership(val role: GroupRole?, val pending: Boolean) {
    val isMember: Boolean get() = role != null

    companion object {
        val NONE = GroupMembership(role = null, pending = false)
    }
}

data class GroupMember(
    val userId: String,
    val role: GroupRole,
    val displayName: String,
    val avatarPath: String?,
    val joinedAt: Instant?,
)

data class JoinRequest(
    val userId: String,
    val displayName: String,
    val avatarPath: String?,
    /** `true` nếu người này từng bị mời ra (FR-008): hiện nhãn "Từng bị mời ra". */
    val wasRemoved: Boolean,
    val createdAt: Instant?,
)

/** Người tham gia một sự kiện (`posts/{postId}/attendees/{uid}`). */
data class Attendee(
    val userId: String,
    val displayName: String,
    val avatarPath: String?,
    val joinedAt: Instant?,
)

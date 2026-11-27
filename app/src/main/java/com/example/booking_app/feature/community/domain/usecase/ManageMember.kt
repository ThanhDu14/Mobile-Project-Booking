package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.GroupRole
import javax.inject.Inject

/** T061: phong/gỡ quản trị viên, chuyển quyền chủ nhóm, mời thành viên ra khỏi nhóm. */
class ManageMember @Inject constructor(
    private val groups: GroupRepository,
) {
    suspend fun setRole(groupId: String, userId: String, role: GroupRole, requestId: String): Result<Unit> =
        groups.setRole(groupId, userId, role, requestId)

    suspend fun transferOwner(groupId: String, userId: String, requestId: String): Result<Unit> =
        groups.transferOwner(groupId, userId, requestId)

    suspend fun remove(groupId: String, userId: String, requestId: String): Result<Unit> =
        groups.remove(groupId, userId, requestId)
}

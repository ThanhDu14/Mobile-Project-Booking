package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.GroupRepository
import javax.inject.Inject

/** T036: rời nhóm. Chủ nhóm chưa chuyển quyền nhận `CommunityError.OwnerMustTransfer(soleMember)`. */
class LeaveGroup @Inject constructor(private val groups: GroupRepository) {
    suspend operator fun invoke(groupId: String, requestId: String): Result<Unit> = groups.leave(groupId, requestId)
}

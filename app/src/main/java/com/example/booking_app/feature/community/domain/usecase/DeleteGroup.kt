package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.GroupRepository
import javax.inject.Inject

/** T061: xóa nhóm (chỉ chủ nhóm, phải gõ đúng tên nhóm `confirmName`, AC 5.6). */
class DeleteGroup @Inject constructor(
    private val groups: GroupRepository,
) {
    suspend operator fun invoke(groupId: String, confirmName: String, requestId: String): Result<Unit> =
        groups.delete(groupId, confirmName, requestId)
}

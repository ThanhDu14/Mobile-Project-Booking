package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.GroupRepository
import javax.inject.Inject

/** T036: chủ nhóm/quản trị viên duyệt hoặc từ chối yêu cầu tham gia. */
class ReviewJoinRequest @Inject constructor(private val groups: GroupRepository) {
    suspend fun approve(groupId: String, userId: String, requestId: String): Result<Unit> =
        groups.approve(groupId, userId, requestId)

    suspend fun reject(groupId: String, userId: String, requestId: String): Result<Unit> =
        groups.reject(groupId, userId, requestId)
}

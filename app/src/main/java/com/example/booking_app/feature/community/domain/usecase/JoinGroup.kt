package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.JoinResult
import javax.inject.Inject

/**
 * T036: tham gia nhóm (công khai vào ngay; riêng tư hoặc người từng bị mời ra thì gửi yêu cầu, server quyết định)
 * và hủy yêu cầu đang chờ. [requestId] do ViewModel sinh để gửi lại an toàn.
 */
class JoinGroup @Inject constructor(private val groups: GroupRepository) {
    suspend operator fun invoke(groupId: String, requestId: String): Result<JoinResult> = groups.join(groupId, requestId)

    suspend fun cancel(groupId: String, requestId: String): Result<Unit> = groups.cancelRequest(groupId, requestId)
}

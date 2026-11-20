package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.Group
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** T026: "Nhóm của tôi", realtime, nhóm `ACTIVE`/`LOCKED` theo `joinedAt` mới nhất trước. */
class ObserveMyGroups @Inject constructor(private val groups: GroupRepository) {
    operator fun invoke(): Flow<List<Group>> = groups.observeMyGroups()
}

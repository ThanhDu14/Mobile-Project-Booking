package com.example.booking_app.feature.community

import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.model.GroupVisibility

const val ME = "me"

fun group(
    id: String = "g1",
    name: String = "CLB Cầu lông",
    description: String = "Mô tả",
    avatarPath: String? = null,
    districtId: String = "q10",
    ownerId: String = ME,
    visibility: GroupVisibility = GroupVisibility.PUBLIC,
    memberCount: Int = 1,
    pendingRequestCount: Int = 0,
    status: GroupStatus = GroupStatus.ACTIVE,
) = Group(id, name, description, avatarPath, districtId, ownerId, visibility, memberCount, pendingRequestCount, status)

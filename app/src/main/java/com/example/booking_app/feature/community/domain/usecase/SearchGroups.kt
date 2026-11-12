package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import javax.inject.Inject

/** T036: tìm nhóm `ACTIVE` theo tên (đã chuẩn hóa) và khu vực, `memberCount` giảm dần, 20 nhóm/trang. */
class SearchGroups @Inject constructor(private val groups: GroupRepository) {
    suspend operator fun invoke(query: String, districtIds: List<String>, cursor: PageCursor?): Result<Page<Group>> =
        groups.search(query, districtIds, cursor)
}

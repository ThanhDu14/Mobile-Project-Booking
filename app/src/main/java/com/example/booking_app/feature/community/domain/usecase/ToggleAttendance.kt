package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.AttendResult
import javax.inject.Inject

/**
 * T055: tham gia / bỏ tham gia sự kiện giao lưu. ViewModel áp dụng cập nhật lạc quan và hoàn lại khi thất bại.
 */
class ToggleAttendance @Inject constructor(
    private val posts: PostRepository,
) {
    /** [attend] `true` để tham gia, `false` để bỏ tham gia. */
    suspend operator fun invoke(groupId: String, postId: String, attend: Boolean): Result<AttendResult> =
        if (attend) posts.attend(groupId, postId) else posts.unattend(groupId, postId)
}

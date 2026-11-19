package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.PostRepository
import javax.inject.Inject

/** T067: chủ nhóm/quản trị viên ẩn hoặc hiện lại bài đăng (bài `SYSTEM` ẩn trả `HiddenBySystem`). */
class ModeratePost @Inject constructor(
    private val posts: PostRepository,
) {
    suspend fun hide(groupId: String, postId: String): Result<Unit> = posts.hide(groupId, postId)

    suspend fun unhide(groupId: String, postId: String): Result<Unit> = posts.unhide(groupId, postId)
}

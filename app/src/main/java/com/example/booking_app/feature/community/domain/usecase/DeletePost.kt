package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.PostRepository
import javax.inject.Inject

/** T048: xóa bài đăng của chính mình (server xóa document, attendees và ảnh). */
class DeletePost @Inject constructor(
    private val posts: PostRepository,
) {
    suspend operator fun invoke(groupId: String, postId: String, requestId: String): Result<Unit> =
        posts.delete(groupId, postId, requestId)
}

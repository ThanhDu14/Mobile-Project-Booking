package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostDraft
import javax.inject.Inject

/** T048: đăng bài hoặc sự kiện mới (`postId = draft.requestId`). */
class PublishPost @Inject constructor(
    private val posts: PostRepository,
) {
    suspend operator fun invoke(groupId: String, draft: PostDraft): Result<Post> = posts.create(groupId, draft)
}

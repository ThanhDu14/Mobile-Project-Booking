package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.ReportReason
import javax.inject.Inject

/** T067: gửi báo cáo bài đăng vi phạm (mỗi người một lần mỗi bài, FR-018). */
class ReportPost @Inject constructor(
    private val posts: PostRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        postId: String,
        reason: ReportReason,
        detail: String?,
    ): Result<String> = posts.report(groupId, postId, reason, detail)
}

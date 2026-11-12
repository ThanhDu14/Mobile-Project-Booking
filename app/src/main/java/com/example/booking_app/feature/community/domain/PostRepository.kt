package com.example.booking_app.feature.community.domain

import com.example.booking_app.feature.community.domain.model.Attendee
import com.example.booking_app.feature.community.domain.model.AttendResult
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostDraft
import com.example.booking_app.feature.community.domain.model.ReportReason
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Bảng tin, sự kiện và báo cáo (T018). Đọc qua Firestore, ghi qua Edge Function `group-post`; thao tác ghi trả
 * `Result.failure(CommunityError)`.
 */
interface PostRepository {
    /**
     * Một trang bảng tin (20 bài, `createdAt` giảm dần). Thành viên thường: bài `VISIBLE` cộng bài `HIDDEN` của chính
     * mình; chủ nhóm/quản trị viên ([isMod]): mọi bài. [PageCursor.key] là `createdAt` tính bằng mili giây.
     */
    suspend fun feed(groupId: String, isMod: Boolean, cursor: PageCursor?): Result<Page<Post>>

    /** Bài mới hơn [after] (realtime) để hiện "Có bài mới" mà không làm mới cả trang; cùng quy tắc lọc với [feed]. */
    fun observeNewPosts(groupId: String, isMod: Boolean, after: Instant): Flow<List<Post>>

    /** Đăng bài hoặc sự kiện; mọi ảnh trong [draft] phải `UPLOADED`. `postId` là `draft.requestId`. */
    suspend fun create(groupId: String, draft: PostDraft): Result<Post>

    /** Sửa bài `draft.requestId` (là `postId`); [draft.photos] là bộ ảnh mới. */
    suspend fun update(groupId: String, draft: PostDraft): Result<Post>

    suspend fun delete(groupId: String, postId: String): Result<Unit>

    suspend fun hide(groupId: String, postId: String): Result<Unit>

    /** Bài admin hệ thống ẩn → `CommunityError.HiddenBySystem`. */
    suspend fun unhide(groupId: String, postId: String): Result<Unit>

    suspend fun attend(groupId: String, postId: String): Result<AttendResult>

    suspend fun unattend(groupId: String, postId: String): Result<AttendResult>

    /** Người tham gia sự kiện, `joinedAt` tăng dần, realtime. */
    fun observeAttendees(groupId: String, postId: String): Flow<List<Attendee>>

    /** Trả `reportId`; [detail] ≤ 500 ký tự, bắt buộc khi [reason] là `OTHER`. */
    suspend fun report(groupId: String, postId: String, reason: ReportReason, detail: String?): Result<String>
}

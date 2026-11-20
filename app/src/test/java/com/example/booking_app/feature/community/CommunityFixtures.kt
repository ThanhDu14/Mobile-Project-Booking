package com.example.booking_app.feature.community

import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.model.HiddenBy
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostAuthor
import com.example.booking_app.feature.community.domain.model.PostStatus
import com.example.booking_app.feature.community.domain.model.PostType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

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

fun post(
    id: String,
    createdAt: Instant? = Instant.parse("2026-11-10T00:00:00Z"),
    authorId: String = "other",
    content: String = "Nội dung $id",
    photoPaths: List<String> = emptyList(),
    status: PostStatus = PostStatus.VISIBLE,
    hiddenBy: HiddenBy? = null,
    authorDeleted: Boolean = false,
    edited: Boolean = false,
    groupId: String = "g1",
) = Post(
    id = id,
    groupId = groupId,
    type = PostType.POST,
    content = content,
    photoPaths = photoPaths,
    author = PostAuthor(authorId, if (authorDeleted) null else "Tác giả $authorId", null, authorDeleted),
    status = status,
    hiddenBy = hiddenBy,
    isEdited = edited,
    createdAt = createdAt,
    event = null,
)

/** Kho bài rỗng cho các test không quan tâm bảng tin. */
fun emptyPostRepository(): PostRepository = mockk {
    coEvery { feed(any(), any(), any()) } returns Result.success(Page(emptyList(), null))
    every { observeNewPosts(any(), any(), any()) } returns emptyFlow()
}

/** Cache URL ảnh không trả URL nào. */
fun noopSignedUrls() = SignedUrlCache({ Result.success(SignedUrlBatch(emptyMap(), null)) }, FakeClock { 0L })

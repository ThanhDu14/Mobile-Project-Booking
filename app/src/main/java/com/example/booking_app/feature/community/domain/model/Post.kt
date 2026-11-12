package com.example.booking_app.feature.community.domain.model

import java.time.Instant

/** Người đăng; [isDeleted] khi đã xóa tài khoản (hiện "Người dùng đã xóa", R16). */
data class PostAuthor(
    val userId: String,
    val displayName: String?,
    val avatarPath: String?,
    val isDeleted: Boolean,
)

/** Phần sự kiện của một bài `EVENT`. Trạng thái hiển thị suy ra ở app, không lưu (data-model mục 5). */
data class EventInfo(
    val title: String,
    val at: Instant,
    val location: String,
    val expectedCount: Int,
    val attendeeCount: Int,
    val iAmAttending: Boolean,
) {
    /** "Đã diễn ra" khi `at ≤ now`. */
    fun isPast(now: Instant): Boolean = !at.isAfter(now)

    /** "Vượt số dự kiến" khi `attendeeCount > expectedCount` (đúng bằng thì chưa vượt). */
    val isOverExpected: Boolean get() = attendeeCount > expectedCount
}

data class Post(
    val id: String,
    val groupId: String,
    val type: PostType,
    val content: String,
    /** Path trong bucket `group-media`, đọc qua `group-media-url`. */
    val photoPaths: List<String>,
    val author: PostAuthor,
    val status: PostStatus,
    val hiddenBy: HiddenBy?,
    val isEdited: Boolean,
    val createdAt: Instant?,
    /** Chỉ có khi [type] là [PostType.EVENT]. */
    val event: EventInfo?,
) {
    val isHidden: Boolean get() = status == PostStatus.HIDDEN
}

/** Ảnh đang đính kèm khi soạn bài; [remotePath] có sau khi tải lên xong. */
data class DraftPhoto(
    val localUri: String,
    val remotePath: String? = null,
    val state: State = State.PENDING,
) {
    enum class State { PENDING, UPLOADING, UPLOADED, FAILED }
}

/**
 * Bản nháp bài đăng. [requestId] là `postId` (research R9), sinh một lần khi mở màn soạn và giữ qua
 * `SavedStateHandle`; khi sửa bài thì là `postId` của bài đang sửa.
 */
data class PostDraft(
    val requestId: String,
    val type: PostType,
    val content: String = "",
    val photos: List<DraftPhoto> = emptyList(),
    val eventTitle: String = "",
    val eventAt: Instant? = null,
    val eventLocation: String = "",
    val expectedCount: Int? = null,
) {
    /** Nút "Đăng" chỉ bật khi không còn ảnh đang tải hoặc lỗi. */
    val photosReady: Boolean
        get() = photos.none { it.state == DraftPhoto.State.UPLOADING || it.state == DraftPhoto.State.FAILED }
}

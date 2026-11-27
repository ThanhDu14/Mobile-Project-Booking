package com.example.booking_app.feature.community.domain.model

import java.time.Instant

/** Con trỏ phân trang: [key] là giá trị sắp xếp (`memberCount` khi tìm nhóm, `createdAt` mili giây khi tải bảng tin) kèm [id] document. */
data class PageCursor(val key: Long, val id: String)

data class Page<T>(val items: List<T>, val next: PageCursor?) {
    val canLoadMore: Boolean get() = next != null

    companion object {
        const val PAGE_SIZE = 20
    }
}

/** Các trường của form tạo/sửa nhóm. */
data class GroupInput(
    val name: String,
    val description: String,
    val districtId: String,
    val visibility: GroupVisibility,
)

/** Đích tải ảnh do server cấp (`{ path, signedUrl, token }`). */
data class SignedUpload(val path: String, val signedUrl: String, val token: String)

data class CreateGroupResult(val group: Group, val avatarUpload: SignedUpload?)

data class UpdateGroupResult(val group: Group, val avatarUpload: SignedUpload?, val autoApproved: Boolean)

/** Một ảnh sắp tải lên cho bài đang soạn (`group-media-url upload`). */
data class UploadFile(val index: Int, val contentType: String, val size: Long)

/** Kết quả `attend`/`unattend`. */
data class AttendResult(val attendeeCount: Int, val attending: Boolean)

data class SignedUrls(val urls: Map<String, String>, val expiresAt: Instant?)

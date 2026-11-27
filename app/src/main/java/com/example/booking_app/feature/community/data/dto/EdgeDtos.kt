@file:OptIn(ExperimentalSerializationApi::class)

package com.example.booking_app.feature.community.data.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Body và response của `group-manage`, `group-post`, `group-media-url` (contracts/edge-functions-community.md).
 * Mỗi function có một class request chung cho mọi action; trường không dùng là `null` và không được gửi
 * (`EncodeDefault.NEVER`), tạo request qua các hàm trong `companion` để không thiếu tham số.
 */
val CommunityFunctionJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

// ---------- group-manage ----------

@Serializable
data class GroupManageRequest(
    val action: String,
    val requestId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val groupId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val description: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val districtId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val visibility: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val hasAvatar: Boolean? = null,
    /** `KEEP`, `REPLACE`, `CONFIRM`, `REMOVE` (chỉ `update`). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val avatar: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val confirmName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val userId: String? = null,
    /** `ADMIN` hoặc `MEMBER` (chỉ `setRole`). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val role: String? = null,
) {
    companion object {
        fun create(requestId: String, name: String, description: String, districtId: String, visibility: String, hasAvatar: Boolean) =
            GroupManageRequest(
                action = "create", requestId = requestId, name = name, description = description,
                districtId = districtId, visibility = visibility, hasAvatar = hasAvatar,
            )

        fun update(
            requestId: String,
            groupId: String,
            name: String?,
            description: String?,
            districtId: String?,
            visibility: String?,
            avatar: String,
        ) = GroupManageRequest(
            action = "update", requestId = requestId, groupId = groupId, name = name, description = description,
            districtId = districtId, visibility = visibility, avatar = avatar,
        )

        fun delete(requestId: String, groupId: String, confirmName: String) =
            GroupManageRequest(action = "delete", requestId = requestId, groupId = groupId, confirmName = confirmName)

        fun join(requestId: String, groupId: String) = GroupManageRequest("join", requestId, groupId = groupId)

        fun cancelRequest(requestId: String, groupId: String) = GroupManageRequest("cancelRequest", requestId, groupId = groupId)

        fun leave(requestId: String, groupId: String) = GroupManageRequest("leave", requestId, groupId = groupId)

        fun approve(requestId: String, groupId: String, userId: String) =
            GroupManageRequest("approve", requestId, groupId = groupId, userId = userId)

        fun reject(requestId: String, groupId: String, userId: String) =
            GroupManageRequest("reject", requestId, groupId = groupId, userId = userId)

        fun setRole(requestId: String, groupId: String, userId: String, role: String) =
            GroupManageRequest("setRole", requestId, groupId = groupId, userId = userId, role = role)

        fun transferOwner(requestId: String, groupId: String, userId: String) =
            GroupManageRequest("transferOwner", requestId, groupId = groupId, userId = userId)

        fun remove(requestId: String, groupId: String, userId: String) =
            GroupManageRequest("remove", requestId, groupId = groupId, userId = userId)
    }
}

/** `group` trong response; cùng tên trường với document `groups/{groupId}` (data-model mục 1). */
@Serializable
data class GroupResultDto(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val avatarPath: String? = null,
    val districtId: String? = null,
    val ownerId: String? = null,
    val visibility: String? = null,
    val memberCount: Int? = null,
    val pendingRequestCount: Int? = null,
    val status: String? = null,
)

/** `{ path, signedUrl, token }` của ảnh đại diện (`group-manage`) và ảnh bài (`group-media-url`). */
@Serializable
data class SignedUploadDto(val path: String, val signedUrl: String, val token: String = "")

/** Response chung của `group-manage`; mỗi action chỉ điền một số trường. */
@Serializable
data class GroupManageResponse(
    val group: GroupResultDto? = null,
    val avatarUpload: SignedUploadDto? = null,
    /** `update`: số người được duyệt tự động khi đổi sang công khai (R12); chấp nhận số hoặc boolean. */
    val autoApproved: JsonElement? = null,
    val deleted: Boolean? = null,
    /** `join`: `JOINED` hoặc `REQUESTED`. */
    val result: String? = null,
) {
    /** `true` khi có ít nhất một người được duyệt tự động. */
    val hasAutoApproved: Boolean
        get() = when (val v = (autoApproved as? JsonPrimitive)) {
            null -> false
            else -> v.booleanOrNull ?: ((v.intOrNull ?: 0) > 0)
        }
}

// ---------- group-post ----------

@Serializable
data class GroupPostRequest(
    val action: String,
    val groupId: String,
    /** `create`: `requestId` = `postId` (R9). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val requestId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val postId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val content: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val photoPaths: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val eventTitle: String? = null,
    /** ISO-8601 (`Instant.toString()`). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val eventAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val eventLocation: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val expectedCount: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val reason: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val detail: String? = null,
) {
    companion object {
        fun create(
            groupId: String,
            postId: String,
            type: String,
            content: String,
            photoPaths: List<String>,
            eventTitle: String?,
            eventAt: String?,
            eventLocation: String?,
            expectedCount: Int?,
        ) = GroupPostRequest(
            action = "create", groupId = groupId, requestId = postId, type = type, content = content,
            photoPaths = photoPaths, eventTitle = eventTitle, eventAt = eventAt, eventLocation = eventLocation,
            expectedCount = expectedCount,
        )

        fun update(
            groupId: String,
            postId: String,
            requestId: String?,
            content: String,
            photoPaths: List<String>,
            eventTitle: String?,
            eventAt: String?,
            eventLocation: String?,
            expectedCount: Int?,
        ) = GroupPostRequest(
            action = "update", groupId = groupId, postId = postId, requestId = requestId, content = content, photoPaths = photoPaths,
            eventTitle = eventTitle, eventAt = eventAt, eventLocation = eventLocation, expectedCount = expectedCount,
        )

        fun delete(groupId: String, postId: String, requestId: String) =
            GroupPostRequest("delete", groupId, postId = postId, requestId = requestId)
        fun hide(groupId: String, postId: String) = GroupPostRequest("hide", groupId, postId = postId)
        fun unhide(groupId: String, postId: String) = GroupPostRequest("unhide", groupId, postId = postId)
        fun attend(groupId: String, postId: String) = GroupPostRequest("attend", groupId, postId = postId)
        fun unattend(groupId: String, postId: String) = GroupPostRequest("unattend", groupId, postId = postId)

        fun report(groupId: String, postId: String, reason: String, detail: String?) =
            GroupPostRequest("report", groupId, postId = postId, reason = reason, detail = detail)
    }
}

/** `post` trong response `create`/`update`; ngày giờ là ISO-8601. */
@Serializable
data class PostResultDto(
    val id: String,
    val groupId: String? = null,
    val authorId: String? = null,
    val authorSnapshot: UserSnapshotDto? = null,
    val authorDeleted: Boolean = false,
    val type: String? = null,
    val content: String? = null,
    val photoPaths: List<String> = emptyList(),
    val eventTitle: String? = null,
    val eventAt: String? = null,
    val eventLocation: String? = null,
    val expectedCount: Int? = null,
    val attendeeCount: Int? = null,
    val status: String? = null,
    val hiddenBy: String? = null,
    val editedAt: String? = null,
    val createdAt: String? = null,
)

/** Response chung của `group-post`. */
@Serializable
data class GroupPostResponse(
    val post: PostResultDto? = null,
    /** `attend`/`unattend`. */
    val attendeeCount: Int? = null,
    val attending: Boolean? = null,
    /** `report`. */
    val reportId: String? = null,
)

// ---------- group-media-url ----------

@Serializable
data class UploadFileDto(val index: Int, val contentType: String, val size: Long)

@Serializable
data class GroupMediaRequest(
    val action: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val groupId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val postId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val files: List<UploadFileDto>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val paths: List<String>? = null,
) {
    companion object {
        fun upload(groupId: String, postId: String, files: List<UploadFileDto>) =
            GroupMediaRequest("upload", groupId = groupId, postId = postId, files = files)

        fun download(paths: List<String>) = GroupMediaRequest("download", paths = paths)

        fun discard(groupId: String, postId: String) = GroupMediaRequest("discard", groupId = groupId, postId = postId)
    }
}

@Serializable
data class GroupMediaResponse(
    /** `upload`. */
    val uploads: List<SignedUploadDto> = emptyList(),
    /** `download`: `{ path: signedUrl }`. */
    val urls: Map<String, String> = emptyMap(),
    /** ISO-8601. */
    val expiresAt: String? = null,
)

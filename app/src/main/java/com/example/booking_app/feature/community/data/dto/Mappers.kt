package com.example.booking_app.feature.community.data.dto

import com.example.booking_app.feature.community.domain.model.Attendee
import com.example.booking_app.feature.community.domain.model.CreateGroupResult
import com.example.booking_app.feature.community.domain.model.EventInfo
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupMember
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.model.HiddenBy
import com.example.booking_app.feature.community.domain.model.JoinRequest
import com.example.booking_app.feature.community.domain.model.JoinResult
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostAuthor
import com.example.booking_app.feature.community.domain.model.PostDraft
import com.example.booking_app.feature.community.domain.model.PostStatus
import com.example.booking_app.feature.community.domain.model.PostType
import com.example.booking_app.feature.community.domain.model.SignedUpload
import com.example.booking_app.feature.community.domain.model.SignedUrls
import com.example.booking_app.feature.community.domain.model.UpdateGroupResult
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date
import java.util.Locale

// ---------- Đọc giá trị thô từ Firestore ----------

internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String

/** `Timestamp`, `Date`, `Instant` hoặc chuỗi ISO-8601; kiểu khác trả `null`. */
internal fun Any?.asInstant(): Instant? = when (this) {
    is Timestamp -> toInstant()
    is Date -> toInstant()
    is Instant -> this
    is String -> runCatching { Instant.parse(this) }.getOrNull()
    else -> null
}

/** Firestore trả số nguyên là `Long`, đôi khi `Double` (ghi từ JS); làm tròn xuống. */
internal fun Any?.asLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}

private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { raw -> enumValues<E>().firstOrNull { it.name == raw.trim().uppercase(Locale.ROOT) } }

// ---------- Firestore → domain ----------

/** `null` khi thiếu `name` hoặc `ownerId` (document hỏng); `visibility`/`status` lạ dùng mặc định `PUBLIC`/`ACTIVE`. */
fun GroupDto.toDomain(): Group? {
    val name = name ?: return null
    val ownerId = ownerId ?: return null
    return Group(
        id = id,
        name = name,
        description = description.orEmpty(),
        avatarPath = avatarPath,
        districtId = districtId.orEmpty(),
        ownerId = ownerId,
        visibility = visibility.toEnumOrNull<GroupVisibility>() ?: GroupVisibility.PUBLIC,
        memberCount = (memberCount ?: 0L).coerceAtLeast(0L).toInt(),
        pendingRequestCount = (pendingRequestCount ?: 0L).coerceAtLeast(0L).toInt(),
        status = status.toEnumOrNull<GroupStatus>() ?: GroupStatus.ACTIVE,
    )
}

/** Vai trò lạ coi như `MEMBER` (không cấp quyền thừa). */
fun MemberDto.toDomain(): GroupMember = GroupMember(
    userId = userId,
    role = role.toEnumOrNull<GroupRole>() ?: GroupRole.MEMBER,
    displayName = snapshot?.displayName.orEmpty(),
    avatarPath = snapshot?.avatarPath,
    joinedAt = joinedAt,
)

fun JoinRequestDto.toDomain(): JoinRequest = JoinRequest(
    userId = userId,
    displayName = snapshot?.displayName.orEmpty(),
    avatarPath = snapshot?.avatarPath,
    wasRemoved = wasRemoved,
    createdAt = createdAt,
)

fun AttendeeDto.toDomain(): Attendee = Attendee(
    userId = userId,
    displayName = snapshot?.displayName.orEmpty(),
    avatarPath = snapshot?.avatarPath,
    joinedAt = joinedAt,
)

/**
 * `null` khi thiếu `authorId`, hoặc là `EVENT` mà thiếu `eventAt`/`eventTitle` (document hỏng). [iAmAttending] lấy từ
 * `attendees/{uid}` của người xem; document bài không chứa thông tin này.
 */
fun PostDto.toDomain(iAmAttending: Boolean = false): Post? {
    val authorId = authorId ?: return null
    val type = type.toEnumOrNull<PostType>() ?: PostType.POST
    val event = if (type == PostType.EVENT) {
        EventInfo(
            title = eventTitle ?: return null,
            at = eventAt ?: return null,
            location = eventLocation.orEmpty(),
            expectedCount = (expectedCount ?: 0L).coerceAtLeast(0L).toInt(),
            attendeeCount = (attendeeCount ?: 0L).coerceAtLeast(0L).toInt(),
            iAmAttending = iAmAttending,
        )
    } else {
        null
    }
    val status = status.toEnumOrNull<PostStatus>() ?: PostStatus.VISIBLE
    return Post(
        id = id,
        groupId = groupId.orEmpty(),
        type = type,
        content = content.orEmpty(),
        photoPaths = photoPaths,
        author = PostAuthor(
            userId = authorId,
            displayName = authorSnapshot?.displayName,
            avatarPath = authorSnapshot?.avatarPath,
            isDeleted = authorDeleted,
        ),
        status = status,
        hiddenBy = if (status == PostStatus.HIDDEN) hiddenBy.toEnumOrNull<HiddenBy>() else null,
        isEdited = editedAt != null,
        createdAt = createdAt,
        event = event,
    )
}

// ---------- Response Edge Function → domain ----------

fun GroupResultDto.toDomain(): Group? = GroupDto(
    id = id,
    name = name,
    description = description,
    avatarPath = avatarPath,
    districtId = districtId,
    ownerId = ownerId,
    visibility = visibility,
    memberCount = memberCount?.toLong(),
    pendingRequestCount = pendingRequestCount?.toLong(),
    status = status,
).toDomain()

fun SignedUploadDto.toDomain(): SignedUpload = SignedUpload(path = path, signedUrl = signedUrl, token = token)

/** `null` khi response không có `group` đọc được (coi như lỗi không rõ). */
fun GroupManageResponse.toCreateResult(): CreateGroupResult? {
    val group = group?.toDomain() ?: return null
    return CreateGroupResult(group, avatarUpload?.toDomain())
}

fun GroupManageResponse.toUpdateResult(): UpdateGroupResult? {
    val group = group?.toDomain() ?: return null
    return UpdateGroupResult(group, avatarUpload?.toDomain(), hasAutoApproved)
}

fun GroupManageResponse.toJoinResult(): JoinResult? = result.toEnumOrNull<JoinResult>()

/** Bài từ response `create`/`update`; `postJson` không trả trạng thái tham gia của người xem nên `iAmAttending = false`. */
fun PostResultDto.toDomain(): Post? = PostDto(
    id = id,
    groupId = groupId,
    authorId = authorId,
    authorSnapshot = authorSnapshot,
    authorDeleted = authorDeleted,
    type = type,
    content = content,
    photoPaths = photoPaths,
    eventTitle = eventTitle,
    eventAt = eventAt.asInstant(),
    eventLocation = eventLocation,
    expectedCount = expectedCount?.toLong(),
    attendeeCount = attendeeCount?.toLong(),
    status = status,
    hiddenBy = hiddenBy,
    editedAt = editedAt.asInstant(),
    createdAt = createdAt.asInstant(),
).toDomain()

fun GroupMediaResponse.toSignedUrls(): SignedUrls = SignedUrls(urls = urls, expiresAt = expiresAt.asInstant())

// ---------- Domain → request ----------

/** `photoPaths` là các ảnh đã `UPLOADED`, giữ thứ tự chọn. */
fun PostDraft.toCreateRequest(groupId: String): GroupPostRequest = GroupPostRequest.create(
    groupId = groupId,
    postId = requestId,
    type = type.name,
    content = content.trim(),
    photoPaths = photos.mapNotNull { it.remotePath },
    eventTitle = eventTitle.takeIf { type == PostType.EVENT }?.trim(),
    eventAt = eventAt?.takeIf { type == PostType.EVENT }?.toString(),
    eventLocation = eventLocation.takeIf { type == PostType.EVENT }?.trim(),
    expectedCount = expectedCount?.takeIf { type == PostType.EVENT },
)

fun PostDraft.toUpdateRequest(groupId: String): GroupPostRequest = GroupPostRequest.update(
    groupId = groupId,
    postId = requestId,
    content = content.trim(),
    photoPaths = photos.mapNotNull { it.remotePath },
    eventTitle = eventTitle.takeIf { type == PostType.EVENT }?.trim(),
    eventAt = eventAt?.takeIf { type == PostType.EVENT }?.toString(),
    eventLocation = eventLocation.takeIf { type == PostType.EVENT }?.trim(),
    expectedCount = expectedCount?.takeIf { type == PostType.EVENT },
)
